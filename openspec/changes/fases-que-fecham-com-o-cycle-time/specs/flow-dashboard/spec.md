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

Each segment SHALL be reported as **the share of the cycle that segment took, applied to the Cycle
Time shown above it**: the segment's hours summed over the population, divided by the three
segments' hours summed over the same population, times the Cycle Time card. The three segments
therefore **add up to the card**, which is what the breakdown is read for — a reader who sees 21 h of
cycle and 1,5 h of phases has no way to know which of the two to believe.

The population SHALL be **exactly the one the Cycle Time card counts**: work items completed in the
period **that entered a working state**, under the same work-type selection, the same container
selection and the same node scope. An item still in progress SHALL NOT contribute, its phase hours
being a partial measurement that can still move in either direction. An item closed without ever
entering a working state SHALL NOT contribute either: it has no cycle, and counting its phases as
zero — which is what happened while "completed" was mistaken for "has a cycle" — dragged every
segment towards zero in teams that close work without moving it on the board. Such items keep
counting in Throughput, which asks a different question.

The segment values SHALL be recomputed over the node's own population, never composed from the
children's values. When the population has no phase hours at all, or there is no Cycle Time to
apply the shares to, the three segments SHALL be zero.

Each segment SHALL be shown **both in hours and as its share of the cycle**. The share is not a
decoration on top of the hours: it is the figure the question is about — "how much of our time is
waiting?" — and it is already what decides the segment's value, so showing one without the other
hides half of what was computed.

The PR-side review time remains available as the `pr_review_time` code drill-down.

#### Scenario: Phase breakdown returns the state segments
- **WHEN** the Fluxo dashboard is requested for a node
- **THEN** the waiting, active and review segments for that node are returned, each derived from the work items' own board states

#### Scenario: The three phases add up to the card
- **WHEN** the population spent 300 h waiting, 100 h active and 100 h in review, and the Cycle Time card reads 20 h
- **THEN** the phases read 12 h waiting, 4 h active and 4 h review, which sum to the card

#### Scenario: An item closed without ever being worked does not drag the phases down
- **WHEN** a node completed eight items that never entered a working state and two that did
- **THEN** only the two contribute to the phases, and the eight still count in Throughput

#### Scenario: A team phase is recomputed, never composed
- **WHEN** a team's segment value is computed and its people have different work-item counts
- **THEN** the value is recomputed over all the team's counted work items, not assembled from per-person values

#### Scenario: An unfinished item does not contribute a partial phase
- **WHEN** a work item is still in progress in the period
- **THEN** its active, waiting and review hours are excluded from the phases

#### Scenario: Each phase is shown in hours and in share
- **WHEN** the phases read 12 h waiting, 4 h active and 4 h review over a 20 h cycle
- **THEN** each is presented with its hours and its share — 60%, 20% and 20%

#### Scenario: Without phase hours the breakdown is zero
- **WHEN** no item of the population carries phase hours
- **THEN** the three phases read zero

#### Scenario: Impeded time appears in the waiting segment
- **WHEN** a work item was impeded while its state classified it as active
- **THEN** that time is reported in the waiting segment and not in the active one

### Requirement: The cycle-time phases follow the filter
The phase breakdown of Cycle Time (waiting, active, review) SHALL be computed over the same
filtered population as the Cycle Time card — the same work-type selection and the same container
selection — so the phases always add up to the value shown above them.

#### Scenario: Phases match the filtered card
- **WHEN** a work type is selected
- **THEN** the phases are computed over the work items of that type, consistent with the filtered Cycle Time

#### Scenario: Phases match the container selection too
- **WHEN** leaves only is selected
- **THEN** the phases are computed over the leaf items alone, and still add up to the filtered Cycle Time

### Requirement: The waiting phase breaks down by impediment reason
The Fluxo dashboard SHALL offer, for the **waiting** phase of the node and period on screen, a
breakdown of the waiting time by **impediment reason**, plus an explicit residual for the waiting
time that carried no impediment signal at all. The breakdown SHALL be computed over the **same
population** as the waiting phase shown beside it and SHALL honour the active work-type filter and
the node scope, so a reader never has to reconcile two different cohorts.

Each part SHALL carry its **share of the population's waiting time**, and those shares SHALL be
applied to the **waiting figure shown on the board**, so the parts add up to exactly the number the
drawer opened from. Reporting the population's raw hours there put the two side by side in different
scales — a board reading 0 h of waiting next to a drawer reading twenty thousand — and left the
reader with no way to tell which number was about their team. The raw population total MAY still be
shown as secondary context, labelled as such.

Each part SHALL likewise be shown **in hours and as its share of the waiting time**, so a reader can
answer "how much of our waiting was infra?" without doing arithmetic on the slices.

The residual SHALL be **derived** from the population's waiting total rather than accumulated
separately, so the two can never disagree. Should the recorded reasons of the population exceed that
waiting total — which stale data can, from before simultaneous reasons were split — the reasons
SHALL be **scaled down proportionally** to fit and the residual SHALL be zero, so the drawer can
never show more hours than the phase it explains.

Free-text reasons SHALL be presented so that the display stays readable when a team invents many:
the larger reasons are shown and the remainder is grouped.

#### Scenario: The breakdown is offered for the waiting phase
- **WHEN** the Fluxo dashboard is requested for a node and period
- **THEN** the waiting time is also returned broken down by impediment reason, with a residual for waiting time that had no signal, and with the total those parts compose

#### Scenario: The parts add up to the waiting figure on the board
- **WHEN** the population spent 1000 h waiting — 600 h on "infra" and 400 h with no signal — and the board shows 8 h of waiting
- **THEN** the drawer shows 4,8 h for "infra" and 3,2 h for the residual, summing to the 8 h on the board

#### Scenario: Each reason is shown in hours and in share
- **WHEN** the drawer shows 4,8 h for "infra" out of 8 h of waiting
- **THEN** it also shows that this is 60% of the waiting time

#### Scenario: Stale reasons are scaled to fit
- **WHEN** the recorded reasons of the population add up to more waiting than the population actually waited
- **THEN** the reasons are scaled down proportionally, the residual is zero, and the parts still sum to the waiting figure

#### Scenario: A team that never marks impediments
- **WHEN** no work item of the node carries an impediment signal
- **THEN** the whole waiting figure is reported as the residual and no reason is listed

#### Scenario: The breakdown follows the work-type filter
- **WHEN** a work-type filter is active
- **THEN** the breakdown is computed over the same filtered population as the waiting phase

## ADDED Requirements

### Requirement: The bottleneck summary names a phase from the data
The Fluxo screen's summary of the biggest bottleneck SHALL name the phase with the **largest value
in the breakdown actually served**, and show that value. When no real breakdown is available it
SHALL name no phase at all.

It SHALL NOT be derived from a synthetic split. The summary read a prototype's fixed weights applied
to the Cycle Time, so it named a bottleneck that no data supported and contradicted the chart
directly above it. Its sub-label SHALL describe what the figure is, rather than calling it an
average it never was.

#### Scenario: The summary follows the breakdown
- **WHEN** the breakdown shows waiting as the largest phase
- **THEN** the summary names waiting and shows the waiting value from that same breakdown

#### Scenario: Without real data no phase is named
- **WHEN** no breakdown is available from the engine
- **THEN** the summary names no phase
