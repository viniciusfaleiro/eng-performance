## Context

The platform reads only `raw_event`; the seed fills it today. S9 replaces the source with real
Azure DevOps, honoring the hexagonal boundary: a new outbound adapter produces the same
`RawEvent`s the engine already consumes. The company blocks PATs, but interactive Entra
device-code auth works in the tenant (verified with a PowerShell reproduction of the exact
flow). So ingestion is **manual and interactive**: an admin triggers a sync, authenticates with
MFA on Microsoft's page, and the app loads events as that user for the duration of a short-lived
token. No PAT, no app registration (a broadly-consented public client id is reused), no stored
secret.

## Goals / Non-Goals

**Goals:**
- Load real Repos/PRs/commits, Pipelines and Boards into `raw_event` behind the existing port,
  so every read view becomes real with no UI change.
- Authenticate interactively (device-code) with no PAT/secret; surface the flow in the Admin UI.
- Support a first backfill and cheap incremental "diff" syncs by watermark; idempotent.

**Non-Goals:**
- No unattended/scheduled sync (that would need a stored credential/service principal) — sync is
  admin-triggered and interactive by design.
- No change to domain/application read logic or to `raw_event`'s shape.
- No live-tenant test in CI — mapping is tested against recorded JSON; the real sync is the
  admin's acceptance step.

## Decisions

- **Device-code auth reusing the `az` public client — no app registration.** The adapter drives
  the Entra device-code endpoints directly (client `04b07795-…`, scope
  `499b84ac-…/.default offline_access`, authority `organizations`). This is what `az login` does,
  and the PowerShell test confirmed the tenant allows it, so no custom Entra app is needed. The
  `offline_access` refresh token lets a long backfill outlive the ~1h access token. An
  `AdoAuthPort` exposes `beginDeviceCode()` → {userCode, verificationUri, deviceCode} and
  `poll(deviceCode)` → token|pending; the adapter implements it with an HTTP client (MSAL4J is an
  option but the raw endpoints are simple and dependency-light). Alternative (confidential client
  + secret, or PAT) rejected — both store a credential the company restricts.
- **Two-phase, async, admin-triggered sync.** `POST /api/admin/ado/sync` starts a background job
  and returns the device-code prompt + a `sessionId`; the job polls for the token, then fetches
  and persists. `GET /api/admin/ado/sync/status?sessionId` returns phase + per-source counts +
  done/failed. The server can't open the admin's browser, so device-code (show a code to enter)
  is the correct server-side interactive flow; a smoother redirect/popup would require an SPA app
  registration and is deferred.
- **A source port, not raw events in the app.** `AdoEventSourcePort.fetchSince(watermark,
  progress)` returns `RawEvent`s already mapped; the application `AdoSyncService` orchestrates:
  read watermark → fetch → `EventStorePort.saveAll` (upsert by id) → advance watermark → record a
  summary. The mapping lives entirely in `adapter-out-ado`; the application never sees ADO types.
- **Map to the existing `RawEvent` contract.** Every `detail` key was defined by an earlier
  slice, so mapping is filling knowns: PR timestamps → `coding_h/pickup_h/review_h/deploy_h`,
  `cycle_h`; PR reviewer votes → `first_pass` and REVIEW events (`decision`/`comments`/`author`);
  pipeline runs → DEPLOY `outcome`/lead/`recovery_hours` under the production-stage rule; work
  items → `type`/`hours`; commit trailer → the AI flag via the existing convention; `_links` →
  `url`. Committer identities and repo keys flow into the existing Admin mapping (coverage badge).
- **Watermark + idempotency.** A `sync_state` row per source holds the last `occurredAt` cursor;
  incremental syncs fetch only newer items; events upsert by their ADO-derived id so re-runs don't
  duplicate. First run backfills a configurable window (6–12 months).
- **Production-stage rule.** A global config names the production stage/environment (with a
  per-team override) so pipeline runs classify into deploys correctly for DORA. Stored with the
  ADO config.
- **Seeder gated to dev.** `EventFixtures` stays for local runs but is disabled once the ADO
  integration is connected, so real and synthetic events never mix; the one-time switch is a
  `TRUNCATE raw_event` before the first real backfill.

## Risks / Trade-offs

- [Access token expiring mid-backfill] → request `offline_access` and refresh with the refresh
  token during the job; the session holds it in memory only for the sync's lifetime.
- [Deriving `first_pass`/reviews from ADO PRs is fiddly] → use PR reviewer votes (approved=10,
  rejected=-10) and thread comments across iterations; covered by mapping tests on recorded PR
  JSON, and imperfect edge cases degrade to "not first-pass" rather than wrong-positive.
- [No live test in CI] → mapping/orchestration unit-tested against recorded JSON fixtures; the
  live sync is the admin acceptance step, and failures surface in the status view, not silently.
- [Large backfill volume/rate limits] → page with continuation tokens, honor ADO rate-limit
  headers, and stream progress; the watermark makes re-runs cheap.
- [ArchUnit boundaries] → only `adapter-out-ado` may import the HTTP/ADO client; domain and
  application stay framework-free, guarded by a new boundary rule.

## Migration Plan

Additive Flyway migration `V4__sync_state.sql`. To switch from seed to real data: connect the
org, `TRUNCATE raw_event` once, then run the first backfill from the Admin button. Rollback =
disconnect the integration and re-enable the seeder; `raw_event` shape is unchanged.

## Open Questions

- Exact backfill window default (6 vs 12 months) — a config value, defaulted to 6, editable in
  Admin.
- Whether to add MSAL4J or keep raw device-code HTTP — decided at implementation; the port
  isolates the choice.
