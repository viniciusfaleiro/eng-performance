## MODIFIED Requirements

### Requirement: Work-item effort is derived from state-transition history
The system SHALL derive a work item's flow measures from its **state-transition history** rather than
the manual `Microsoft.VSTS.Scheduling.CompletedWork` field. For each work item in the incremental
delta (those whose `System.ChangedDate` is at/after the sync watermark), the system SHALL fetch the
item's update history and reconstruct the **time spent in each `System.State`** from the state
transitions (each transition's revised timestamp and before/after value). Each state SHALL be
classified into **one of three categories** — **active**, **wait** (blocked/idle) or **terminal** —
by its Azure DevOps state **category** (`Proposed`, `InProgress`, `Resolved`, `Completed`, `Removed`)
when available, with a **configurable name-based fallback** (e.g. `Blocked`, `On Hold`, `Waiting`,
`Ready for…` → wait) — never hardcoded to a single process template. From this the work-item raw
event SHALL carry: the item **type**, the **active** duration, the **wait** duration, the **cycle**
(first active state to terminal), the **creation** and **completion** timestamps, and a
**completed** marker when a terminal state was reached. The system MUST NOT fetch the whole backlog:
only the incremental delta's items are queried, one history request per item (the update API has no
batch).

#### Scenario: Active, wait and cycle reconstructed from transitions
- **WHEN** a work item moved New → Active → Blocked → Active → Resolved → Closed with recorded timestamps
- **THEN** its raw event carries the summed active time, the summed wait time, the cycle from the first Active to Closed, and the completion timestamp, attributed to the item's type

#### Scenario: State categories drive the active/wait/terminal split
- **WHEN** a state exposes an Azure DevOps state category
- **THEN** it is classified as active, wait or terminal by that category, falling back to the configurable name heuristic only when the category is unavailable

#### Scenario: Only the incremental delta is queried for history
- **WHEN** an incremental sync runs after a watermark
- **THEN** update history is fetched only for work items changed at/after the watermark, one request per item, not for the whole backlog

#### Scenario: A work item with no usable transition is "no data", not zero
- **WHEN** a work item has no state transition in the fetched history (e.g. created and closed in the same instant, or the watermark cut its history)
- **THEN** it produces no active/wait/cycle measure and is reported as "no data" for coverage — never a silent zero
