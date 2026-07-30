## 1. Three-way state classifier (adapter-out-ado)

- [x] 1.1 Extend the work-item state classifier from `{in-progress, terminal}` to `{active, wait, terminal}`: map by ADO state **category** (`InProgress`→active, `Resolved`/`Completed`/`Removed`→terminal, `Proposed`→wait) with a name fallback for wait (`blocked`, `on hold`, `waiting`, `ready for…`).
- [x] 1.2 Make the wait/active keyword lists configurable via platform config (not hardcoded); default keywords documented.
- [x] 1.3 Unit-test the classifier: category-driven mapping, name fallback, and unknown → active.

## 2. Work-item mapping carries the flow measures (adapter-out-ado)

- [x] 2.1 In `AdoMapper.workItem`, reconstruct from the state history: `active_h`, `wait_h`, `cycle_h` (first active → terminal), `lead_h` (created → completed), and a `completed` marker; keep the in-progress `spans` for the individual distribution.
- [x] 2.2 Set the WORKITEM event `occurredAt` to the completion timestamp when the item is completed, else its `ChangedDate`.
- [x] 2.3 Emit "no data" (absent measures) when there is no usable transition — never a silent zero.
- [x] 2.4 Feed the PR's `review_h` into the item's `review` segment via the AB# link when present; absent link → review segment "no data".
- [x] 2.5 Unit-test the mapping: active/wait/cycle/lead reconstruction, completed marker + completion-dated event, and the no-data case.

## 3. Metric catalog wiring (application, engine unchanged)

- [x] 3.1 Re-point `cycle_time` → MEDIAN of `cycle_h` over WORKITEM, population `completed == 1`.
- [x] 3.2 Re-point `throughput` → SUM over WORKITEM, population `completed == 1` (count of completed items), higher-is-better.
- [x] 3.3 Re-point `flow_efficiency` → RATIO `num = active_h`, `den = active_h + wait_h` over WORKITEM.
- [x] 3.4 Add `flow_lead_time` → MEDIAN of `lead_h` over WORKITEM, population `completed == 1`, lower-is-better, unit "h", distinct from DORA `lead_time`.
- [x] 3.5 Reconcile `wip` requirement/label to the shipped count of in-progress items ("itens").
- [x] 3.6 Reframe the phase segments to `wait_h` / `active_h` / `review_h` / `deploy_h`; keep `pr_size` and `pr_review_time` on PR as code drill-downs.
- [x] 3.7 Unit-test the catalog: each re-pointed metric reads the right measure/population and aggregation.

## 4. Dashboard composition + screen (application, adapter-in-web)

- [x] 4.1 Update `FlowDashboardService` + DTOs to surface the board-anchored cards, the new Flow Lead Time, the state-segment breakdown, and the code drill-downs section.
- [x] 4.2 Update the served Fluxo screen labels/grouping (delivery metrics vs code drill-downs; WIP in "itens"); keep scope enforcement and prototype parity.
- [x] 4.3 Update `FlowDashboardService`/heatmap tests for the new sources; confirm Comparativo/Individual read the new values with no structural break.

## 5. Verification

- [x] 5.1 Run `./gradlew spotlessApply build` — all gates green (Spotless, Checkstyle, SpotBugs, JaCoCo, ArchUnit).
- [ ] 5.2 Manual acceptance: run a sync (or "Reprocessar 6 meses") against a real org and sanity-check Cycle Time/Throughput/Flow Efficiency/Flow Lead Time and the active/wait split; confirm items without usable history show as reduced coverage, not zero.
