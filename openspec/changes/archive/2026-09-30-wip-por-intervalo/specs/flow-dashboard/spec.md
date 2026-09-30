## MODIFIED Requirements

### Requirement: WIP counts work items in progress
The system SHALL compute **WIP** as the **count of work items that were in progress at any moment
inside the selected period** — an item whose in-progress interval, reconstructed from its own state
history, overlaps the period — reported as a count ("itens"), lower-is-better, with
correct-polarity evolution and coverage. A count is concurrency-safe: many simultaneously open items
cannot inflate it the way summing each item's hours did. Work items with no usable state history
SHALL be reflected in coverage as "no data", never counted.

An item SHALL be counted whether or not it was **completed** inside the period: a task in progress on
the 3rd and finished on the 10th was in progress during that month. WIP therefore includes the
period's completed work, and the difference between WIP and Throughput is what stayed open.

The count SHALL NOT depend on the item's record date. Neither the last time the board was touched
nor the completion date decides membership — only the overlap does.

Because a longer period overlaps more items, **WIP is not comparable across periods of different
lengths**: a month necessarily counts more items than a day. Comparison against the previous period
of equal length remains valid, and the panel SHALL state that the figure is "items in progress
during the period" rather than a count of items open simultaneously.

#### Scenario: WIP is a count of in-progress items
- **WHEN** the Fluxo dashboard is requested for a node with several work items in progress
- **THEN** WIP is the number of those items, in "itens", not a sum of hours

#### Scenario: WIP is not inflated by concurrency
- **WHEN** one person has many work items open at once
- **THEN** WIP counts the items and is not multiplied by each item's open duration

#### Scenario: An open item untouched for weeks still counts today
- **WHEN** a work item has been in progress since last month, is still open, and nobody has changed it
- **THEN** it counts in today's WIP, in this week's and in this month's

#### Scenario: An item completed later still counts in the period it was worked
- **WHEN** a work item was in progress during June and completed in August
- **THEN** June's WIP counts it

#### Scenario: An item completed inside the period counts
- **WHEN** a work item was in progress on the 3rd and completed on the 10th of the selected month
- **THEN** it counts in that month's WIP

#### Scenario: An item that only sat in the backlog does not count
- **WHEN** a work item existed during the period but never entered a working state
- **THEN** it is not counted

### Requirement: Cycle Time breaks down into four phases
The system SHALL model a work item's cycle as ordered **state segments** — waiting, active, review
and done/deploy — reconstructed from the item's **own state-transition history** (its board columns),
not from the PR review window nor from a linked deploy. Each state SHALL be assigned to a segment by
its classification (waiting/active/terminal) plus a review sub-label (states named like *code
review*/*testing* → the review segment). Cycle Time SHALL be the median of the per-item
active-to-terminal duration.

Each segment value at a node SHALL be the median of that segment over the node's **completed**
work items — the same population as Cycle Time — recomputed and not composed from children. An item
still in progress SHALL NOT contribute: its phase hours are a partial measurement that can still
move in either direction, and mixing it in made the phases describe a different population than the
Cycle Time shown beside them. The segment medians still need not sum to the Cycle Time median, since
each is a median in its own right. The PR-side review time remains available as the
`pr_review_time` code drill-down.

#### Scenario: Phase breakdown returns the state segments
- **WHEN** the Fluxo dashboard is requested for a node
- **THEN** the waiting, active, review and done/deploy segment medians for that node are returned, each from the work items' own board states

#### Scenario: Team phase is the population median
- **WHEN** a team's segment value is computed and its people have different work-item counts
- **THEN** the value is the median of that segment over all the team's completed work items, not the average of per-person medians

#### Scenario: An unfinished item does not contribute a partial phase
- **WHEN** a work item is still in progress in the period
- **THEN** its active, waiting and review hours are excluded from the phase medians

### Requirement: Flow Efficiency is active over total time
The system SHALL compute Flow Efficiency as **active time over active plus wait time** over the work
item's board life, where active = time in states classified as active and wait = time in states
classified as waiting/blocked (per the ado-integration three-way state classification). It SHALL be
a volume-weighted ratio (sum of active over sum of active+wait) across the node's **completed**
work-item population and is higher-is-better.

Work items with no usable state history SHALL be excluded and reflected in coverage, never counted
as zero — including by the aggregation's own defaults: an item that carries no active/wait
measurement SHALL NOT be read as zero active over one hour of total, which would pull the ratio down
for having no data rather than for waiting.

#### Scenario: Flow Efficiency equals active over active plus wait
- **WHEN** Flow Efficiency is computed for a node
- **THEN** it equals the summed active hours divided by the summed active-plus-wait hours across the node's completed work items

#### Scenario: Items without transitions are excluded
- **WHEN** some of a node's work items have no usable state transition
- **THEN** they are excluded from Flow Efficiency and lower its coverage, rather than counted as zero

#### Scenario: An item with no measurement does not enter the denominator
- **WHEN** a work item carries no active or wait hours at all
- **THEN** it contributes nothing to either side of the ratio, instead of contributing zero over one
