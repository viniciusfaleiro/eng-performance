## MODIFIED Requirements

### Requirement: WIP counts work items in progress
The system SHALL compute **WIP** as the **count of work items that were committed and not delivered
at any moment inside the selected period** — an item whose **committed stretch**, reconstructed from
its own state history (per the ado-integration capability), overlaps the period — reported as a
count ("itens"), lower-is-better, with correct-polarity evolution and coverage. A count is
concurrency-safe: many simultaneously open items cannot inflate it the way summing each item's hours
did. Work items with no usable state history SHALL be reflected in coverage as "no data", never
counted.

The committed stretch spans **queue and work alike** — from the moment the team took the item on
until it was delivered — and SHALL exclude the stretches the item was **impeded**. Counting only the
time someone had a hand on the item made the metric blind where work actually stalls: an item
finished and parked waiting for deploy is the most expensive inventory a team holds, and it used to
disappear from the number that exists to expose it.

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

#### Scenario: An item waiting in a delivery queue counts
- **WHEN** a work item spent the period finished but parked in a queue before production
- **THEN** it counts in that period's WIP

#### Scenario: An item impeded for the whole period does not count
- **WHEN** a work item was marked as impeded for the entire selected period
- **THEN** it is not counted, because the impeded stretches are not part of the committed stretch

## ADDED Requirements

### Requirement: WIP is offered only at the daily frequency
WIP SHALL be presented **only when the selected frequency is daily**, and SHALL be absent — not
greyed, not zeroed — from the Fluxo cards, from the trend metric selection and from the comparison
heatmap at any other frequency.

The reason is the metric's own arithmetic: membership is decided by overlap, so the same item counts
in every period it crosses. The days of a week do not add up to the week, and a month necessarily
counts more items than a day. A figure that cannot be summed or compared across lengths invites
exactly those two readings whenever it is on screen next to figures that can.

#### Scenario: WIP appears at daily frequency
- **WHEN** the Fluxo dashboard is requested at the daily frequency
- **THEN** the WIP card is present

#### Scenario: WIP is absent at weekly and monthly
- **WHEN** the Fluxo dashboard is requested at the weekly or monthly frequency
- **THEN** no WIP card is shown

#### Scenario: WIP cannot be charted outside the daily frequency
- **WHEN** a user is choosing a metric to chart at a non-daily frequency
- **THEN** WIP is not among the options
