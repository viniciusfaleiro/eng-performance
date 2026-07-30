## MODIFIED Requirements

### Requirement: The Fluxo metrics are available
The system SHALL provide the Fluxo metrics computed by the metrics engine, anchored on the **work
item** (Azure Boards) for the delivery metrics and on the PR/commit only for the code drill-downs:
**Cycle Time** (work-item active-to-terminal time, person-scoped), **Throughput** (count of work
items completed in the period), **WIP** (count of work items in progress), **Flow Efficiency**
(active over active+wait), and **Flow Lead Time** (work item created to completed). It SHALL also
provide the code sub-process drill-downs **PR Review Time** and **PR Size** (median changed lines),
computed from PR/commit events and clearly presented as diagnostics, not the Fluxo headline. Each
metric SHALL report its value, correct-polarity evolution, and coverage for the requested node and
frequency. Fluxo metrics carry no DORA benchmark tier.

#### Scenario: Fluxo metrics computed for a node
- **WHEN** the Fluxo dashboard is requested for a node and frequency
- **THEN** Cycle Time, Throughput, WIP, Flow Efficiency and Flow Lead Time are returned with value and evolution, plus PR Review Time and PR Size as code drill-downs

#### Scenario: Delivery metrics come from the board, not the PR
- **WHEN** Cycle Time, Throughput and Flow Efficiency are computed for a node
- **THEN** they are derived from the node's work items' state history, so work with no code is included and the PR window is not mistaken for the whole cycle

### Requirement: Cycle Time breaks down into four phases
The system SHALL model a work item's cycle as ordered **state segments** — waiting, active, review
and done/deploy — reconstructed from the item's **own state-transition history** (its board columns),
not from the PR review window nor from a linked deploy. Each state SHALL be assigned to a segment by
its classification (waiting/active/terminal) plus a review sub-label (states named like *code
review*/*testing* → the review segment). Cycle Time SHALL be the median of the per-item
active-to-terminal duration. Each segment value at a node SHALL be the median of that segment over the
node's whole work-item population (recomputed, not composed from children); the segment medians need
not sum to the Cycle Time median. The PR-side review time remains available as the `pr_review_time`
code drill-down.

#### Scenario: Phase breakdown returns the state segments
- **WHEN** the Fluxo dashboard is requested for a node
- **THEN** the waiting, active, review and done/deploy segment medians for that node are returned, each from the work items' own board states

#### Scenario: Team phase is the population median
- **WHEN** a team's segment value is computed and its people have different work-item counts
- **THEN** the value is the median of that segment over all the team's work items, not the average of per-person medians

### Requirement: Flow Efficiency is active over total time
The system SHALL compute Flow Efficiency as **active time over active plus wait time** over the work
item's board life, where active = time in states classified as active and wait = time in states
classified as waiting/blocked (per the ado-integration three-way state classification). It SHALL be
a volume-weighted ratio (sum of active over sum of active+wait) across the node's work-item
population and is higher-is-better. Work items with no usable state history SHALL be excluded and
reflected in coverage, never counted as zero.

#### Scenario: Flow Efficiency equals active over active plus wait
- **WHEN** Flow Efficiency is computed for a node
- **THEN** it equals the summed active hours divided by the summed active-plus-wait hours across the node's work items

#### Scenario: Items without transitions are excluded
- **WHEN** some of a node's work items have no usable state transition
- **THEN** they are excluded from Flow Efficiency and lower its coverage, rather than counted as zero

## ADDED Requirements

### Requirement: Throughput counts completed work items
The system SHALL compute **Throughput** as the number of work items that reached a **terminal**
state within the period, attributed as-of the completion, higher-is-better. It SHALL NOT count pull
requests. A work item that is still open (no terminal transition in the period) SHALL NOT contribute
to Throughput.

#### Scenario: Throughput counts items completed in the bucket
- **WHEN** three work items reach a terminal state in a bucket and one stays open
- **THEN** Throughput for that bucket is three

#### Scenario: Throughput ignores pull-request count
- **WHEN** a period has many PRs but few work items completed
- **THEN** Throughput reflects the completed work items, not the PR count

### Requirement: Flow Lead Time spans creation to completion
The system SHALL provide **Flow Lead Time** as the median time from a work item's **creation** to its
**completion** (terminal state), person-scoped, lower-is-better, distinct from the DORA `lead_time`
(change to production). It SHALL be reported in hours with evolution and coverage; items not yet
completed and items with no usable history SHALL be excluded from the value and reflected in
coverage.

#### Scenario: Flow Lead Time measured from creation to done
- **WHEN** a work item is created and later reaches a terminal state
- **THEN** its Flow Lead Time is the hours between creation and completion, and the node value is the median across completed items

#### Scenario: Flow Lead Time is distinct from DORA lead time
- **WHEN** both Flow Lead Time and the DORA lead_time are shown
- **THEN** they are labelled and computed separately (board created→done vs change→production)

### Requirement: WIP counts work items in progress
The system SHALL compute **WIP** as the **count of work items in progress** in the period — items in
a state classified as active (or waiting/blocked but not terminal) during the bucket — reported as a
count ("itens"), lower-is-better, with correct-polarity evolution and coverage. A count is
concurrency-safe: many simultaneously open items cannot inflate it the way summing each item's hours
did. Work items with no usable state history SHALL be reflected in coverage as "no data", never
counted.

#### Scenario: WIP is a count of in-progress items
- **WHEN** the Fluxo dashboard is requested for a node with several work items in progress
- **THEN** WIP is the number of those items, in "itens", not a sum of hours

#### Scenario: WIP is not inflated by concurrency
- **WHEN** one person has many work items open at once
- **THEN** WIP counts the items and is not multiplied by each item's open duration

## REMOVED Requirements

### Requirement: WIP measures time spent in progress states
**Reason**: Superseded by a count-based WIP. Summing/median of each item's in-progress hours
inflated the value for many concurrent or long-open items and read in hours despite an "items"
intent.
**Migration**: WIP now reads the count of work items in progress in the period (see the new
"WIP counts work items in progress" requirement); the in-progress duration remains available through
Cycle Time and the active/wait segments.
