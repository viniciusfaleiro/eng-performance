## Context

The Fluxo metrics today are PR-anchored: `cycle_time`, `throughput`, `flow_efficiency` (and the four
phases) read `EventType.PR`, so they measure the code-review window only. The work-item
state-history machinery already exists (`AdoMapper` reconstructs in-progress spans from
`.../workitems/{id}/updates`, classifying states via ADO state **category** with a name fallback),
and feeds WIP + the individual work-type distribution. This change re-points the delivery metrics at
that machinery and extends it. The metrics engine (`MetricsEngine`) already supports the
aggregations we need — SUM (count), MEDIAN, RATIO (num/den), and a population predicate — so no
engine primitive is new.

## Goals / Non-Goals

**Goals:**
- Cycle Time, Throughput, Flow Efficiency, and a new Flow Lead Time computed from the work item's
  state history, so they include non-code work and reflect the real board lifecycle.
- A three-way state classifier (active / wait / terminal) reused by all board metrics.
- Keep PR/commit metrics (`pr_size`, `pr_review_time`, PR coding time, assertiveness) as code
  drill-downs, unchanged in source.
- Reconcile WIP (already shipped as a count) with the spec.

**Non-Goals:**
- No change to DORA or AI metrics; no board UI (CFD/Kanban); no new ingestion scope.
- Not deleting the PR-derived phase data used as code drill-down.

## Decisions

### 1. WORKITEM event carries the derived measures in `detail` (+ occurredAt = completion)
`AdoMapper.workItem` will additionally emit: `active_h`, `wait_h`, `cycle_h`, `lead_h`
(created→completed), `completed` (`1`/absent), and keep the in-progress `spans` for the individual
distribution. The event's `occurredAt` will be the **completion timestamp when the item is
completed**, else its `ChangedDate` — so completion-dated metrics bucket correctly.
- *Why:* the engine buckets by `occurredOn`; anchoring completed items at completion lets Throughput
  and Flow Lead Time bucket without a new engine concept.
- *Alternative considered:* emit a separate `WORKITEM_DONE` event type. Rejected — more event types,
  more mapping, and the same item would appear twice; a single event with a `completed` marker is
  simpler and idempotent by id.

### 2. Metric catalog wiring (measure + population), engine unchanged
- `throughput` → `SUM` over `WORKITEM`, population = `completed == 1` (uses the engine's existing
  population predicate). Counts completed items in the bucket.
- `cycle_time` → `MEDIAN` of `cycle_h` over `WORKITEM`, population = `completed == 1`.
- `flow_lead_time` (new) → `MEDIAN` of `lead_h` over `WORKITEM`, population = `completed == 1`.
- `flow_efficiency` → `RATIO` with `num = active_h`, `den = active_h + wait_h` over `WORKITEM`.
- `wip` → `SUM` over `WORKITEM` (count), population = not completed in-bucket (in-progress) —
  reconciles the shipped count.
- The **phase segments** become `wait_h` / `active_h` / `review_h` / `deploy_h`; `review_h` comes
  from the PR linked to the item, `deploy_h` from the linked deploy when present.
- *Why:* keeps all logic declarative in `MetricCatalog`; the engine already resolves a named measure
  and a population predicate.

### 3. Three-way state classifier, configurable
Extend the classifier used by `inProgressSpans` from `{in-progress, terminal}` to
`{active, wait, terminal}`. Category mapping: `InProgress`→active, `Resolved`/`Completed`/`Removed`
→terminal, `Proposed`→wait (backlog). Name fallback (when category absent): `blocked`, `on hold`,
`waiting`, `ready for` → wait; otherwise active until a terminal name. The wait keyword list is
configurable (platform config), not hardcoded.
- *Why:* Flow Efficiency needs active-vs-wait; ADO has no "wait" category, so a tuned heuristic is
  unavoidable — matching the repo's "tuned against a real org during acceptance" stance.

### 4. Code metrics stay on PR
`pr_size`, `pr_review_time`, and PR `coding_time` remain `EventType.PR` and are surfaced as
diagnostics. The PR's `review_h` is also fed into the item's `review` segment via the AB# link when
available; without the link, the review segment is "no data" (coverage), not zero.

## Risks / Trade-offs

- [Active/wait split is heuristic when ADO omits state categories] → configurable keyword list; when
  neither category nor keyword resolves, default to active and surface reduced confidence via
  coverage; document as acceptance-tuned.
- [Items completed before their history window (watermark) lack transitions] → excluded as "no
  data", never zero; a full re-sync ("Reprocessar 6 meses") backfills.
- [Throughput/Cycle depend on a terminal transition existing] → items never moved to a terminal
  state are simply not counted (open WIP), which is correct; coverage shows the gap.
- [PR↔work-item link (AB#) may be missing] → the review segment and any PR enrichment fall back to
  "no data"; the core board metrics still compute from state history alone.
- [Numbers shift meaningfully for existing dashboards] → this is the intended correction; call it out
  in release notes and require a re-sync before reading the new Fluxo.

## Migration Plan

1. Ship mapping + classifier + catalog wiring behind the normal sync.
2. Existing events without the new `detail` fields fall back to "no data" for the re-anchored
   metrics until re-synced.
3. Operator runs "Reprocessar 6 meses" to backfill `active_h`/`wait_h`/`cycle_h`/`lead_h`/`completed`.
4. Rollback: revert the catalog wiring commit — the engine and event store are unaffected (extra
   `detail` keys are inert).

## Open Questions

- Exact ADO state **category** strings per process template (Basic/Agile/Scrum/CMMI) to confirm the
  active/wait/terminal mapping against a real org during acceptance.
- Whether `Proposed` (backlog) time should count as wait in Flow Efficiency or be excluded until the
  first active state (leaning: exclude pre-active backlog; efficiency measures active-vs-wait *after*
  work started).
