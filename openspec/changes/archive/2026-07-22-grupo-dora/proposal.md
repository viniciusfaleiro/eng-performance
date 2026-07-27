## Why

The metrics engine (S3) can compute any catalog metric per node/frequency, but the
platform still has no composed dashboard — the DORA card grid in the served prototype
is a stub. S4 delivers the first real dashboard: the four complete DORA metrics with
benchmark tiers and structure rankings, all reading live data from the engine.

Three of the four DORA metrics already exist in the catalog as S3 samples
(`deploy_freq`, `lead_time`, `cfr`). S4 adds the fourth (`mttr`), the benchmark
tiers, the rankings, and turns the prototype's DORA screen from mock into a
real-data dashboard. It is mostly composition on top of S3 — the engine, RBAC and
node-aware endpoints are already in place.

## What Changes

- **MTTR** added to the catalog: recovery duration is pre-computed at ingestion (a
  recovery deploy carries `recovery_hours`); MTTR = MEDIAN over those. No new engine
  aggregation — the same MEDIAN as Lead Time.
- **Metric measure selection**: `MetricDefinition` gains a `measure` field naming
  what each metric reads (`value` = numericValue, or a named detail key such as
  `recovery_hours`), so Lead Time and MTTR can both be MEDIAN over deploys yet measure
  different things. Default `value` keeps existing metrics unchanged.
- **Deploy outcome**: DEPLOY events carry `detail.outcome ∈ {success, failed,
  recovery}`. CFR = failed / all deploys (recovery counts as non-failure); a recovery
  deploy additionally carries `recovery_hours` for MTTR. CFR migrates from a raw
  `num/den` flag to deriving it from `outcome`.
- **Benchmark tiers**: a domain `Tier` (ELITE/ALTO/MEDIO/BAIXO) with global,
  direction-aware DORA thresholds declared per metric. Only DORA metrics classify.
- **Rankings**: compare structures, never people (coaching-only preserved). Rank the
  children of the current node (all→verticals, vertical→teams, team→none), ordered by
  metric value respecting direction, Top-N, scope-enforced.
- **Composed endpoint** `/api/dashboards/dora?node=&freq=` returning the four DORA
  cards (value + tier + evolution + coverage) and the ranking, node-aware and
  scope-enforced.
- **DORA screen** leaves the stub and reads real engine data (cards, tier badges,
  hero, ranking Top-N, tier table, biggest/smallest movers).
- **Seed** evolves to generate deterministic paired failed→recovery deploys.

## Capabilities

### New Capabilities
- `dora-dashboard`: the DORA metric group (the four metrics incl. MTTR and its
  recovery-pairing model, the metric `measure` selector, deploy outcome semantics),
  benchmark tiers, structure rankings, and the composed DORA dashboard endpoint +
  screen.

### Modified Capabilities
- `metrics-engine`: `MetricDefinition` gains `measure` (which per-event field a
  metric reads) and optional benchmark tier bands; the engine reads the declared
  measure instead of assuming `numericValue`. Backward-compatible (default `value`).

## Impact

- **Modules touched:** `domain` (Tier + bands, `measure` on MetricDefinition, MTTR),
  `application` (engine reads `measure`; DORA dashboard use-case: cards+tiers+ranking;
  catalog adds mttr), `adapter-in-web` (`/api/dashboards/dora` + DTOs; DORA screen
  wired), `bootstrap` (event seeder emits outcome + paired recoveries),
  `architecture-tests` (boundaries hold).
- **Reuses:** S3 engine (attribution, roll-up, bucketing, coverage), S2 AccessScope
  (node visibility, coaching-only).
- **DB:** no schema change (`detail` jsonb already holds `outcome`/`recovery_hours`);
  applying S4 requires a one-time reseed of `raw_event` (dev step, in tasks).
- **No new gates or dependencies**; closes with `./gradlew build` green + chrome
  visual parity (numbers reflect the engine).
