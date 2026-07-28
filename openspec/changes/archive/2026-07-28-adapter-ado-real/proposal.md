## Why

S1–S8 built the whole platform behind `EventStorePort`, always reading `raw_event` and never
talking to Azure DevOps. S9 is the payoff: swap the **source** of events from the seed to the
real Azure DevOps, without touching domain or application. The company does not issue PATs, so
ingestion authenticates **interactively** with Microsoft Entra (device-code) — the exact flow
`az login` uses, verified to work in the tenant. There is no PAT, no stored secret, no service
principal: an admin clicks **Sincronizar** in the app, logs in with MFA on Microsoft's page,
and the app loads events as that user.

## What Changes

- **New module `adapter-out-ado`**: the real outbound adapter for Azure DevOps.
- **Interactive device-code auth** (no PAT, no app registration): the adapter begins a device
  code against Entra (public client id `04b07795-8ddb-461a-bbee-02f9e1bf7b46`, scope
  `499b84ac-1321-427f-aa17-267ca6975798/.default offline_access`, authority `organizations`),
  surfaces the user-code + verification URL to the Admin UI, polls for the user's token after
  MFA, and refreshes it silently during a long load. Nothing is persisted but the short-lived
  session token in memory for the duration of the sync.
- **Fetch & map to `RawEvent`**: pull Repos/PRs/commits, Pipelines (deploys) and Boards
  (work items), mapping each to the existing `RawEvent` contract — the `detail` keys every
  slice already defined (`cycle_h`, `first_pass`, review `decision`/`comments`/`author`,
  deploy `outcome`/`recovery_hours`, work-item `type`/`hours`, the AI trailer flag, `url`
  deep-links). Commit AI detection reuses the existing convention; committer identities feed
  the existing Admin → Identidades mapping; repos feed repo→team mapping.
- **Admin-triggered async sync**: `POST /api/admin/ado/sync` starts a background job and
  returns the device-code prompt; `GET /api/admin/ado/sync/status` streams progress
  (counts per source, phase, done/failed). First run **backfills 6–12 months**; later runs are
  **incremental** by a per-source watermark (the "diff"). The Admin → Integração ADO screen
  gains the Sincronizar button, the device-code prompt, the progress view, and last-sync info.
- **Production-stage rule**: a global rule for which pipeline/environment counts as a
  production deploy, with a per-team override (needed to classify DORA deploys from real
  pipelines).
- **Idempotent persistence**: events upsert by id via the existing `EventStorePort`; a small
  `sync_state` table holds the watermark and last-sync summary. The dev seeder stays for local
  use but is off when the ADO integration is connected.

## Capabilities

### New Capabilities
- `ado-integration`: interactive (device-code) Azure DevOps ingestion — auth, fetch/map of
  Repos/PRs/commits, Pipelines and Boards into `RawEvent`, the production-stage rule, and the
  admin-triggered async sync (backfill + incremental watermark) with its progress UI.

### Modified Capabilities
<!-- None at the requirement level: the adapter produces the same RawEvents the engine already
     consumes, and reuses the existing identity/repo mapping and AI convention. -->

## Impact

- **Modules touched:** new `adapter-out-ado` (device-code auth client, ADO REST client, RawEvent
  mapping, sync orchestration), `application` (`AdoSyncUseCase` inbound port + a `AdoEventSource`/
  `AdoAuth` outbound port + sync service + `sync_state` port), `adapter-out-persistence`
  (`sync_state` table via Flyway V4 + repository), `adapter-in-web` (`/api/admin/ado/sync` +
  status + DTOs; Integração ADO screen wired), `bootstrap` (wiring; seeder gated to dev),
  `architecture-tests` (the new module obeys the boundaries — only the adapter sees the ADO SDK).
- **Reuses:** `EventStorePort`, the `RawEvent` contract, identity/repo mapping, the AI
  convention, and the whole read side (every dashboard becomes real with no UI change).
- **New dependency:** an HTTP/JSON client for the ADO REST API (and optionally MSAL4J for token
  handling); no PAT, no stored credential.
- **DB:** one additive migration (`sync_state`); no change to `raw_event`.
- **Testing boundary:** mapping and orchestration are unit-tested here against **recorded ADO
  JSON fixtures** (no tenant access in CI); the live device-code sync is validated by the admin
  on a real org. Closes with `./gradlew build` green + the Admin sync flow demonstrated end to
  end by the user.
