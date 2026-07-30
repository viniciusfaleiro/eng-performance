## Why

Today the Fluxo group measures the **pull-request lifecycle** (open → merge), i.e. only the code
window. So Cycle Time looks artificially fast (it ignores the time the work sat on the board before
and around coding) and Throughput/Flow Efficiency **exclude work that has no code** (analysis,
design, ops, support). The PRD (docs/initial-spec.md) frames Fluxo as *delivery* metrics; those are
properly anchored on the **work item** (Azure Boards), with the PR as a sub-step — not the ruler.

## What Changes

- **BREAKING** — **Cycle Time** is re-anchored on the work item: from its first **active** state to
  its **terminal** state, from the item's state-transition history (not the PR open→merge window).
- **BREAKING** — **Throughput** counts **work items completed** (that entered a terminal state) in
  the period, instead of counting PRs. A work item is the unit of delivered value; a PR is not.
- **BREAKING** — **Flow Efficiency** becomes active ÷ (active + wait) over the work item's board
  life, replacing the PR-derived coding÷cycle ratio.
- **NEW** — a **flow lead time** metric: work item **created → completed**, distinct from the DORA
  `lead_time` (change → production). Both are kept, clearly labelled.
- The **cycle-time phases** are reframed as **work-item state segments** (e.g. waiting/To-Do →
  active → review → done), where the PR review window is **one** segment, not the whole ruler.
- The work-item **state classifier is extended from 2 categories** (in-progress / terminal) **to 3**
  — **active / wait(blocked) / terminal** — by Azure DevOps state **category** when available, with
  a configurable name heuristic (`Blocked`, `On Hold`, `Waiting`, `Ready for…`), never hardcoded.
- Code metrics — `pr_size`, `pr_review_time`, PR `coding_time`, assertiveness — are **demoted to
  drill-down/diagnostics** of the code sub-process. They keep coming from PRs/commits and are no
  longer the Fluxo headline.
- **WIP** is reconciled to its shipped behaviour: a **count of work items in progress** in the
  period (concurrency-safe), superseding the earlier "median in-progress hours" requirement.
- Items with **no usable state history** are **"no data"** (lower coverage), never a silent zero;
  coverage reflects the dependence on conventions 20/21 (typed work item, linked to the PR, board
  states that separate *doing* from *waiting*).

## Capabilities

### New Capabilities
<!-- none: all changes modify existing capabilities -->

### Modified Capabilities
- `flow-dashboard`: Cycle Time, Throughput and Flow Efficiency change source from the PR to the work
  item; the four PR phases become work-item state segments; a new **flow lead time** metric is
  added; `pr_size`/`pr_review_time` are reframed as code drill-down; the WIP requirement is
  reconciled to a count of in-progress items.
- `ado-integration`: the work-item mapping additionally derives, from the state history, the item's
  **cycle** (first active → terminal), **active** vs **wait** durations, and **completion**
  timestamp; the state classifier is extended to three categories (active / wait / terminal).

## Non-goals

- No change to **DORA** metrics (`deploy_freq`, `lead_time`, `cfr`, `mttr`) — they stay on
  deploy/pipeline events.
- No change to **AI** metrics or the contribution calendar (commit-based).
- Not building a full Kanban board view or cumulative-flow diagram; only the metric definitions.
- No new work-item ingestion beyond the incremental window already synced (still one history call
  per item, no backlog scan).
- Not enforcing the board conventions — only reflecting missing/ambiguous state data as reduced
  coverage.

## Impact

- **Specs**: `openspec/specs/flow-dashboard`, `openspec/specs/ado-integration`.
- **Code**: `MetricCatalog` (source/aggregation of `cycle_time`, `throughput`, `flow_efficiency`,
  phases, new flow lead time), `AdoMapper`/`AdoEventSource` (work-item event carries cycle, active,
  wait, completion; 3-way state classifier), `FlowDashboardService` and its DTOs, and the served
  Fluxo screen labels. The `metrics-engine` aggregations (SUM/MEDIAN/RATIO, population split) are
  reused unchanged.
- **Data**: takes effect for **re-synced** work items ("Reprocessar 6 meses"); pre-existing events
  without the new state fields fall back to "no data" for the re-anchored metrics.
- **Dashboards**: Individual panel's delivery trends inherit the new cycle/throughput source (same
  metric keys); Comparativo/heatmap read the new values with no structural change.
