## Context

S4–S6 built three composed dashboards over the metrics engine, each following the same
shape: per-node cards from `MetricsQueryUseCase.cards`, a coaching-safe children ranking,
and scope enforcement in the web adapter using the S2 `AccessScope`. The Comparativo view
is the cross-cutting counterpart: a single heatmap comparing a node's children (rows)
across **every** catalog metric (columns), coloured by relative standing among peers. The
prototype's Comparativo is still synthetic. It is also the first view whose rows can be
**people** — a manager comparing their own reports — so it is where the S2 coaching rule
(`canViewIndividual`) first drives a real query.

## Goals / Non-Goals

**Goals:**
- Ship the Comparativo as real engine data: a node-aware children × all-metrics matrix
  endpoint and the wired screen at chrome parity.
- Reuse the engine cards and the IA `ai_impact` composition — no new metric, no engine
  change, no schema change, no reseed.
- Enforce the coaching-safe rule end-to-end: people rows only for the managing/own account.

**Non-Goals:**
- No new aggregation or catalog entry; the heatmap only reads existing metrics.
- No server-side ranking/colouring — relative shading stays a presentation concern.
- No per-person public exposure: people rows are gated to the manager/own account, exactly
  as the S2 scope already allows (a person's row is coaching data, shown only to whoever may
  view that individual).

## Decisions

- **One row = one child node's cards.** For each child the service calls
  `metrics.cards(child, freq)` and reads the twelve catalog metrics in column order
  (`deploy_freq`, `lead_time`, `cfr`, `mttr`, `cycle_time`, `throughput`, `wip`,
  `pr_review_time`, `pr_size`, `flow_efficiency`, `ai_share`, `ai_adoption`), then appends
  the composed `ai_impact`. This is the same value the dashboards show, so a cell and its
  dashboard card always agree. Alternative (a bespoke matrix query in the engine) was
  rejected as duplicating roll-up already done by `cards`.
- **`ai_impact` is reused, not recomputed inline.** The IA impact computation
  (cycle-time delta between the AI and non-AI cohorts) is extracted into a public
  `AiDashboardUseCase.impact(node, freq)` returning its `AiCard`; both the IA dashboard and
  the heatmap call it. This avoids recomputing the whole IA dashboard per row and keeps a
  single definition of impact. Columns therefore span all thirteen metrics like the mock.
- **Row selection mirrors the prototype and is scope-gated.** Rows are the children of the
  node: overview → teams (default) or verticals (a `scope` query param toggle); vertical →
  its teams; team → its people; person → the person's team colleagues. Structure rows are
  filtered by `AccessScope.canView`; **person rows by `canViewIndividual`** — so an
  org-wide/exec account viewing a team sees no person rows, while the team's manager (whose
  scope carries those `personIds`) sees them. The base node itself is checked with
  `canView` (403 otherwise), identical to the dashboards.
- **Relative colouring is client-side.** The endpoint returns raw metric values plus the
  metric order/labels; the screen keeps its existing per-column best/intermediate/worst
  shading and the same unit normalisation it already applies on the dashboards
  (`deploy_freq` per day, ratios as percentages). Keeping colour in the client means the
  endpoint is a clean data matrix and the heat table markup is unchanged (chrome parity).

## Risks / Trade-offs

- [Computing every metric for every child is N×M engine passes] → the dataset is
  fixture-sized and the engine reads are already what the dashboards do; acceptable now, and
  the roll-up cost is unchanged. If it ever matters, cards for all children could be batched.
- [A manager’s heatmap leaking a colleague outside their reports] → person rows are filtered
  strictly by `canViewIndividual`; covered by a web test asserting an org-wide viewer gets no
  person rows and a manager gets only their own team’s people.
- [Numbers diverging from the mock] → expected and per the S3–S6 rule: chrome matches, the
  values are the real engine values (the prototype’s heat colours were computed off synthetic
  numbers).

## Migration Plan

No schema change, no reseed. Purely additive: a new read endpoint + screen wiring. Rollback
= revert the change; nothing else references the new endpoint.

## Open Questions

- None blocking. The `scope` toggle only applies at the overview node (teams vs verticals);
  every other node has a single, structurally-determined child type.
