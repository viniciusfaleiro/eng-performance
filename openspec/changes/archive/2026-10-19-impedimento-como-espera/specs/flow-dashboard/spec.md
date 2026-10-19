## MODIFIED Requirements

### Requirement: Cycle Time breaks down into four phases
The system SHALL model a work item's cycle as ordered **state segments** — waiting, active, review
and done/deploy — reconstructed from the item's **own state-transition history** (its board columns),
not from the PR review window nor from a linked deploy. Each state SHALL be assigned to a segment by
its classification (waiting/active/terminal) plus a review sub-label (states named like *code
review*/*testing* → the review segment). Cycle Time SHALL be the median of the per-item
active-to-terminal duration.

The time during which an item was **impeded** (per the impediment-signal capability) SHALL be
reported in the **waiting** segment even when the item's state classified it as active or review.
The segments therefore describe where the item's time went, not only which column it sat in: on a
board with no blocked column, an item parked inside an active column is waiting, and reporting it as
active overstated the active segment and understated the waiting one.

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

#### Scenario: Impeded time appears in the waiting segment
- **WHEN** a work item was impeded while its state classified it as active
- **THEN** that time is reported in the waiting segment and not in the active one

### Requirement: Flow Efficiency is active over total time
The system SHALL compute Flow Efficiency as **active time over active plus wait time** over the work
item's board life, where active = time in states classified as active and wait = time in states
classified as waiting/blocked (per the ado-integration three-way state classification), **less the
time the item was impeded**, which belongs to wait wherever it occurred (per the impediment-signal
capability). It SHALL be a volume-weighted ratio (sum of active over sum of active+wait) across the
node's **completed** work-item population and is higher-is-better.

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

#### Scenario: Impediment lowers Flow Efficiency
- **WHEN** part of a work item's active time was impeded
- **THEN** that time counts on the wait side of the ratio, lowering Flow Efficiency

## ADDED Requirements

### Requirement: The waiting phase breaks down by impediment reason
The Fluxo dashboard SHALL offer, for the **waiting** phase of the node and period on screen, a
breakdown of the waiting time by **impediment reason**, plus an explicit residual for the waiting
time that carried no impediment signal at all. The breakdown SHALL be computed over the **same
population** as the waiting phase shown beside it and SHALL honour the active work-type filter and
the node scope, so a reader never has to reconcile two different cohorts.

The breakdown SHALL describe the **composition** of that population's waiting time: the reasons and
the residual together SHALL account for the whole of it, and each SHALL carry its share of it. It
SHALL NOT claim to decompose the waiting **card**, which is a median of the per-item waiting hours:
a median is not the sum of anything, and presenting slices as if they added up to it would invent a
reconciliation that cannot exist. The total the shares refer to SHALL be stated wherever they are
shown.

The residual SHALL be **derived** from the population's waiting total rather than accumulated
separately, so the two can never disagree.

Free-text reasons SHALL be presented so that the display stays readable when a team invents many:
the larger reasons are shown and the remainder is grouped.

#### Scenario: The breakdown is offered for the waiting phase
- **WHEN** the Fluxo dashboard is requested for a node and period
- **THEN** the waiting time is also returned broken down by impediment reason, with a residual for waiting time that had no signal, and with the total those parts compose

#### Scenario: The parts account for the population's waiting time
- **WHEN** the reasons and the residual of the breakdown are summed
- **THEN** the sum equals the total waiting hours of the same population the waiting phase was computed over, and each part's share is of that total

#### Scenario: A team that never marks impediments
- **WHEN** no work item of the node carries an impediment signal
- **THEN** the whole waiting figure is reported as the residual and no reason is listed

#### Scenario: The breakdown follows the work-type filter
- **WHEN** a work-type filter is active
- **THEN** the breakdown is computed over the same filtered population as the waiting phase

### Requirement: The waiting segment opens its breakdown
The served UI SHALL let a user open the waiting phase from the phase breakdown and see the
impediment reasons behind it, and SHALL show that the segment can be opened rather than relying on
the user discovering it. The remaining segments keep their current behaviour.

#### Scenario: Opening the waiting segment
- **WHEN** a user activates the waiting segment or its legend entry on the phase card
- **THEN** the UI opens the waiting metric's panel showing the breakdown by impediment reason

#### Scenario: The segment looks openable
- **WHEN** the phase card is rendered
- **THEN** the waiting segment carries an affordance showing it can be opened, and is reachable by keyboard
