## Context

S3 built the metrics engine (attribution, roll-up, `measure` selector, RATIO via
`detail.num/den`, coverage, as-of-event) and S4 added the composed-dashboard pattern
(cards, children ranking, scope enforcement). The Fluxo group reuses all of this: it
adds catalog metrics and a composed dashboard, without changing any engine
requirement. The prototype's Fluxo dashboard is still a stub reading synthetic values.

The five design decisions below were locked with the product owner in a design review
(grill). They are the source of truth for implementation.

## Goals / Non-Goals

**Goals:**
- Cycle time with a four-phase breakdown, PR size, and flow efficiency computed by the
  engine per node/frequency, plus the reused throughput/WIP/PR-review metrics.
- A composed `/api/dashboards/flow` (cards + phases + scatter) and a real-data Fluxo
  screen at chrome parity.
- A throughput×cycle scatter that compares structures only (coaching-safe).

**Non-Goals:**
- IA (S6), comparativo/heatmap (S7), individual panel (S8), real ADO (S9).
- Any new engine aggregation type or capability — everything reuses S3/S4.
- Per-team goals/thresholds (Fluxo metrics have no DORA-style tier).

## Decisions

### 1. Cycle time = four phase durations in `detail`, MEDIAN of the sum
Each PR event carries `coding_h`, `pickup_h`, `review_h`, `deploy_h`, and `cycle_h`
(their sum) in `detail`. `cycle_time` is a MEDIAN metric with `measure=cycle_h`; each
phase is a MEDIAN metric reading its own key (`coding_time`→`coding_h`,
`pickup_time`→`pickup_h`, `deploy_time`→`deploy_h`); the **review phase reuses the
existing `pr_review_time`** (its measure = the event's numeric review hours). All are
PERSON-scoped (PR attributed to its author via committer identity → person,
as-of-event), LOWER_BETTER, unit `h`, group `fluxo`. The engine is unchanged — this is
exactly the `measure` mechanism added in S4.

### 2. Phase roll-up = median of each phase over the population
At any node, each phase's value is the median of that phase across the node's whole PR
population — recomputed, not composed from children (the S3 rule). The four phase
medians need **not** sum to the cycle-time median; that is expected and honest (a
median of sums ≠ sum of medians). The phase breakdown (the prototype's phase block) is
these four independent medians.

### 3. PR size
`pr_size` = MEDIAN/person, `measure=lines` (changed lines carried in `detail.lines`),
LOWER_BETTER, unit `linhas`, group `fluxo`.

### 4. Flow efficiency = active / cycle
`flow_efficiency` = RATIO/person, HIGHER_BETTER, unit `%`. Active = coding + review;
waiting = pickup + deploy; FE = active / (active + waiting) = active / cycle. The
engine's RATIO reads `detail.num` / `detail.den`, so the seed writes `detail.num` =
active hours (coding_h + review_h) and `detail.den` = cycle_h on PR events. No
collision with CFR (RATIO on DEPLOY events) — different event types.

### 5. Scatter/comparison compare structures, never people
The throughput×cycle scatter plots the **children of the node**: at the overview the
verticals, in a vertical its teams, and for a team **no public scatter** (individual
comparison is coaching-only → the S8 panel); a person is never plotted. Same
coaching-safe rule as the DORA ranking, enforced by the access scope (a base node
outside scope is 403; entries are filtered to viewable nodes). There is no code path
that compares people publicly.

### 6. Composed endpoint + screen
`GET /api/dashboards/flow?node=&freq=` returns: the six Fluxo cards (cycle_time,
throughput, wip, pr_review_time, pr_size, flow_efficiency — value + evolution +
coverage, no tier); the four-phase breakdown (coding/pickup/review/deploy medians for
the node); and the scatter (children with throughput on X and cycle_time on Y). The
prototype's Fluxo dashboard (card grid, cycle-time trend, ranking, throughput×cycle
scatter, phase block) leaves the stub and reads this real data.

### 7. Deterministic seed with phase fields
`EventFixtures` evolves so each PR event carries `coding_h`, `pickup_h`, `review_h`,
`deploy_h`, `cycle_h` (=sum), `lines`, and the flow-efficiency RATIO fields
(`detail.num`=active, `detail.den`=cycle). Deterministic (no randomness, no
wall-clock; anchored dates). The seeder is idempotent on `count() > 0`, so applying S5
needs a one-time `TRUNCATE raw_event` (dev step in tasks).

## Risks / Trade-offs

- **Phase medians don't sum to the cycle median:** the honest per-phase medians won't
  add up to `cycle_time`. The screen presents them as independent phase medians, not a
  strict decomposition; documented so it isn't read as a bug.
- **Parity divergence is intended:** the Fluxo card grid, phase block, ranking and
  scatter show real engine numbers that differ from the prototype's synthetic values.
  Parity = chrome fidelity (0px on layout/frames/labels); numbers reflect the engine.
  Same rule as S3/S4, through S8.
- **Reseed is destructive (dev only):** the one-time `TRUNCATE raw_event` discards the
  S4-seeded events; acceptable because they are synthetic dev data regenerated
  idempotently. The new PR events add fields; DORA/deploy events are re-seeded
  unchanged so S4 stays intact.
- **RATIO overloads `detail.num/den`:** flow efficiency and CFR both use the generic
  RATIO fields, disambiguated only by event type (PR vs DEPLOY). Safe today; if a
  second RATIO metric on the same event type is ever needed, the engine would need a
  numerator/denominator measure selector (deferred).
- **review phase reuses `pr_review_time`:** keeping one metric for both the card and
  the phase avoids duplication, but couples the phase block to that metric's
  definition; acceptable since they are the same quantity.
