## Context

S3 shipped the metrics engine: `raw_event` (COMMIT/PR/DEPLOY/WORKITEM), person/repo
attribution (as-of-event), sum/median/ratio/snapshot roll-up (median/ratio recomputed
over the population), Diário/Semanal/Mensal bucketing, correct-polarity evolution,
unattributed bucket + coverage, and node-aware `/api/metrics/{catalog,cards,series}`
enforcing the S2 access scope. The catalog already carries `deploy_freq` (SUM/repo),
`lead_time` (MEDIAN/repo) and `cfr` (RATIO/repo) as samples. What's missing for a real
DORA dashboard: MTTR, benchmark tiers, rankings, the composed endpoint, and turning
the prototype's DORA screen from stub into real data.

The five design decisions below were locked with the product owner in a design review
(grill). They are the source of truth for implementation.

## Goals / Non-Goals

**Goals:**
- The four complete DORA metrics computed by the engine per node/frequency, each with
  its correct benchmark tier and correct-polarity evolution.
- MTTR modeled without adding a new engine capability.
- Structure rankings that never expose individuals (coaching-only preserved).
- A composed `/api/dashboards/dora` and a real-data DORA screen at chrome parity.

**Non-Goals:**
- Fluxo (S5), IA (S6), comparativo/heatmap (S7), individual panel (S8).
- Real Azure DevOps ingestion (S9) — the seed emits synthetic outcomes/recoveries.
- Per-team configurable goals/thresholds (backlog / future phase).
- Read-time event pairing or new aggregation types — pairing is pre-computed.

## Decisions

### 1. MTTR = pre-computed recovery duration, explicit recovery deploy
MTTR is the time from a failed production deploy to the deploy that restored it.
Rather than teach the engine to pair events at read time, the **recovery is
pre-computed at ingestion**: the recovering deploy carries `recovery_hours` (hours
since the paired failure), and `mttr` is `MEDIAN(recovery_hours)` over recovery
deploys — the *same* MEDIAN the engine already runs for Lead Time. The seeder does the
pairing today; the S9 ADO adapter will do it at sync time. `mttr`: aggregation MEDIAN,
scope repo, direction LOWER_BETTER, unit `h`. Rationale: keeps the engine generic
(no new "duration between paired events" concept); pairing is a source-side concern.

### 2. Metric `measure` selector
Lead Time and MTTR are both MEDIAN of hours over DEPLOY events but measure different
things (lead hours vs recovery hours). `MetricDefinition` gains a `measure` field
naming what the metric reads: `"value"` (numericValue) or a named `detail` key
(`"recovery_hours"`). `lead_time.measure = value`; `mttr.measure = recovery_hours`;
`cfr` reads `num/den`. The engine reads the declared measure for MEDIAN/SNAPSHOT
instead of assuming numericValue. Default `value` → all S3 metrics unchanged.
Rationale: one small, explicit, generalizing engine change; avoids numericValue
meaning different things on different events.

### 3. Deploy outcome semantics
DEPLOY events carry `detail.outcome ∈ {success, failed, recovery}`:
- **CFR** = count(failed) / count(all deploys). A recovery counts as **non-failure**
  (denominator = all; numerator = failed only). CFR migrates from a raw `num/den` flag
  to deriving `num = outcome==failed ? 1 : 0`, `den = 1`.
- **recovery** deploys additionally carry `detail.recovery_hours` for MTTR.
- `deploy_freq` counts all deploys (SUM); `lead_time` is MEDIAN(lead hours) over all
  deploys. A recovery deploy is a successful deploy that also carries recovery timing.

### 4. Global, direction-aware benchmark tiers
A domain `Tier` (ELITE/ALTO/MEDIO/BAIXO) with global DORA thresholds declared per
metric (in the catalog/domain, not configurable in the MVP). Only DORA metrics
classify; others return no tier. Thresholds (direction-aware):

| Metric | Elite | Alto | Médio | Baixo |
|---|---|---|---|---|
| Deployment Frequency (deploys/dia) | ≥ 1/dia | ≥ 1/semana | ≥ 1/mês | abaixo |
| Lead Time (h) | < 24 | < 168 | < 720 | ≥ 720 |
| Change Failure Rate (%) | ≤ 15 | ≤ 30 | ≤ 45 | > 45 |
| MTTR (h) | < 1 | < 24 | < 168 | ≥ 168 |

DF is higher-better; LT/CFR/MTTR are lower-better. Deployment Frequency is classified
on a **normalized deploys-per-day** value so the tier is independent of the selected
bucket size.

### 5. Rankings compare structures, never people
Rankings rank the **children of the current node**: node=`all` → verticals; a vertical
→ its teams; a team → **no ranking** (show the team's own detail); a person → never.
Entries are ordered by the metric value respecting direction (best first), Top-N. The
ranking only includes nodes the caller's `AccessScope` permits; a base node outside
scope is 403. This preserves the coaching-only invariant by construction — there is no
code path that ranks people.

### 6. Composed endpoint + screen
`GET /api/dashboards/dora?node=&freq=` returns the four DORA cards (value, tier,
evolution, coverage) and the ranking of the node's children, node-aware and
scope-enforced (reusing the S2/S3 guard; `/api/metrics/*` stays available). The
prototype's DORA dashboard (card grid with tier badges, hero, Top-N ranking, tier
table, biggest/smallest movers) leaves the stub and reads this real data.

### 7. Deterministic seed with paired recoveries
`EventFixtures` evolves to emit DEPLOY events with `outcome`, and for each `failed`
deploy a later `recovery` deploy on the same repo carrying a deterministic
`recovery_hours`. No randomness, no wall-clock — anchored dates. Because the seeder is
idempotent on `count() > 0`, applying S4 needs a one-time `TRUNCATE raw_event` (dev
step in tasks) so the new outcome-bearing events replace the S3 seed.

## Risks / Trade-offs

- **Parity divergence is intended:** the DORA card grid now shows real engine numbers,
  which differ from the prototype's synthetic values. Parity = chrome fidelity (0px on
  layout/badges/table/ranking); the numbers reflect the engine. Same rule locked in S3,
  applies through S4–S8.
- **DF tier normalization:** classifying Deployment Frequency requires converting the
  bucket count to deploys/day; the normalization constant (bucket length in days) must
  be explicit or the tier flips with frequency. Handled in the tier evaluator.
- **Reseed is destructive (dev only):** the one-time `TRUNCATE raw_event` discards the
  S3-seeded events; acceptable because they are synthetic dev data re-generated
  idempotently. Documented in tasks; never run against real data (S9 owns real events).
- **CFR semantics of recovery:** counting a recovery deploy as a non-failure success is
  a deliberate DORA reading (it's a deployment that succeeded); it slightly raises the
  denominator vs. counting only original deploys. Documented so it isn't mistaken for a
  bug.
- **measure default:** existing metrics rely on `measure` defaulting to `value`; the
  field must be non-breaking (optional with a default) to avoid touching S3 catalog
  entries or the ratio path.
