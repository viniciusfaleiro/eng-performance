## Why

With the engine (S3) and the DORA dashboard pattern (S4) in place, the Fluxo group is
mostly composition: new catalog metrics + a composed dashboard, reusing the `measure`
selector, the RATIO path, the roll-up, and the coaching-safe ranking already built.
The prototype's Fluxo dashboard is still a stub; S5 turns it into real engine data.

Fluxo answers "how does work flow?": cycle time broken into four phases
(coding → pickup → review → deploy), throughput, WIP, PR review time, PR size, and
flow efficiency — with a throughput×cycle scatter that compares structures (never
people).

## What Changes

- **Cycle time (4 phases)**: each PR event carries `coding_h`, `pickup_h`, `review_h`,
  `deploy_h` (and `cycle_h` = their sum) in `detail`. `cycle_time` = MEDIAN of `cycle_h`;
  each phase is a MEDIAN metric reading its detail key via the S4 `measure` selector
  (`coding_time`, `pickup_time`, `deploy_time`; the review phase reuses the existing
  `pr_review_time`). No new engine capability.
- **Phase roll-up**: each phase value at a node is the median of that phase over the
  node's whole PR population (independent — the four phase medians need not sum to the
  cycle median). The phase breakdown is composed of these four medians.
- **PR size**: new `pr_size` = MEDIAN/person, `measure=lines`, lower-is-better.
- **Flow efficiency**: new `flow_efficiency` = RATIO/person, higher-is-better, where
  active = coding + review and FE = active / cycle. The seed writes `detail.num` =
  active hours and `detail.den` = cycle hours on PR events (no collision with CFR,
  which is RATIO on DEPLOY events).
- **Composed endpoint** `/api/dashboards/flow?node=&freq=`: the six Fluxo cards
  (value + evolution + coverage, no DORA tier), the four-phase breakdown, and the
  scatter of the node's children (throughput × cycle) — node-aware, scope-enforced.
- **Scatter/ranking** compare structures only (children of the node), never people —
  the same coaching-safe rule as the DORA ranking.
- **Fluxo screen** leaves the stub and reads real engine data (cards, cycle trend,
  ranking, scatter, phase block).
- **Seed** evolves PR events to carry the phase durations, `cycle_h`, active/cycle
  ratio fields, and `lines`.

## Capabilities

### New Capabilities
- `flow-dashboard`: the Fluxo metric group (cycle time + four phases, PR size, flow
  efficiency), the composed Fluxo dashboard endpoint (cards + phase breakdown +
  throughput×cycle scatter), and the coaching-safe comparison rule.

### Modified Capabilities
<!-- None: metrics-engine already exposes `measure` (added in S4); Fluxo only adds
     catalog entries and a composed dashboard, changing no engine requirement. -->

## Impact

- **Modules touched:** `application` (catalog adds cycle_time/coding/pickup/deploy/
  pr_size/flow_efficiency; `FlowDashboardService` for cards + phases + scatter),
  `adapter-in-web` (`/api/dashboards/flow` + DTOs; Fluxo screen wired), `bootstrap`
  (PR seed carries phase/lines fields), `architecture-tests` (boundaries hold).
- **Reuses:** S3 engine (`measure`, RATIO, roll-up, coverage, as-of-event), S4 pattern
  (composed dashboard, children ranking, scope enforcement), existing `throughput`/
  `wip`/`pr_review_time`.
- **DB:** no schema change (`detail` jsonb holds the new keys); applying S5 requires a
  one-time reseed of `raw_event` (dev step, in tasks).
- **No new gates/dependencies**; closes with `./gradlew build` green + chrome parity
  (numbers reflect the engine). DORA (S4) stays intact.
