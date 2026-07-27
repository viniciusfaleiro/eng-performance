## Why

The platform can register structure, accounts and RBAC (S1–S2), but it does not yet
**measure** anything. Every metric-group dashboard (DORA/Fluxo/IA — S4–S6) needs the
same underlying machinery: raw events, a metric catalog, and an aggregation engine
that rolls values up the Vertical→Time→Pessoa hierarchy over Diário/Semanal/Mensal
buckets. Building that engine once, generically, keeps the later group slices thin
(catalog entries + a screen) instead of each reinventing aggregation.

This is the foundation slice (S3): the metrics engine plus the navigation shell and
the **Tendências** view, wired against seeded synthetic events. The real Azure DevOps
adapter (S9) later only fills the same events behind the port — it changes the
*source*, not the *engine*.

## What Changes

- **Raw events** persisted durably in Postgres as a single polymorphic `raw_event`
  (COMMIT/PR/DEPLOY/WORKITEM) with common dimensions + a `detail` payload, behind an
  `EventStorePort`.
- **Metric catalog** with, per metric: `attributionScope` (person|repo),
  `aggregation` (sum|median|ratio|snapshot), `direction` and `sentiment`. S3 seeds a
  representative subset (one metric per aggregation×attribution combination); the
  DORA/Fluxo/IA groups add their own metrics in S4–S6.
- **On-read aggregation engine** (framework-free application layer): two attribution
  paths (CommitterIdentity→Person, Repository→Team), **as-of-event** attribution via
  the S1 TeamMembership history, hierarchy roll-up where median/ratio are recomputed
  over the event population (never composed from children), Diário/Semanal/Mensal
  bucketing, evolution vs. the previous period with correct polarity, and an
  **"Não atribuído" bucket + coverage %** for events that match no person/repo.
- **Node-aware metrics endpoints** `/api/metrics/catalog`, `/api/metrics/cards`,
  `/api/metrics/{key}/series`, all enforcing the S2 access scope (403 outside scope;
  individuals coaching-only).
- **Navigation shell** (frequency + view selector) and the **Tendências** view,
  server-rendered reusing the prototype design system, at 0px parity for the parts
  this slice ships.
- **Deterministic seed** of ~6 months of synthetic events for the S1 people/repos.

## Capabilities

### New Capabilities
- `metrics-engine`: raw event model, metric catalog schema, and the on-read
  aggregation engine (attribution paths, as-of-event, roll-up semantics, bucketing,
  evolution/polarity, unattributed + coverage).
- `metrics-navigation`: node-aware metric endpoints, the frequency/view navigation
  shell, and the Tendências view — all respecting the S2 access scope.

### Modified Capabilities
<!-- None: S2 authorization is consumed (scope enforcement) but its requirements do not change. -->

## Impact

- **New modules touched:** `domain` (RawEvent, MetricDefinition, aggregation value
  objects), `application` (EventStorePort, aggregation engine + metrics use-cases),
  `adapter-out-persistence` (raw_event JPA + Flyway migration, EventStore adapter,
  event seeder), `adapter-in-web` (metrics controllers + DTOs, shell + Tendências in
  the served prototype), `bootstrap` (wiring + seeder order), `architecture-tests`
  (boundaries hold).
- **Reuses:** S1 structure/membership (as-of-event), S1 CommitterIdentity/Repository
  (attribution), S2 AccessScope (node visibility).
- **DB:** new `raw_event` table via Flyway; durable, seeded idempotently.
- **No new gates or dependency changes**; closes with `./gradlew build` green +
  visual parity.
