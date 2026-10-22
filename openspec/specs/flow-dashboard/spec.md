# flow-dashboard Specification

## Purpose
The Fluxo metric group over the metrics engine, anchored on the **work item** (Azure Boards) for the
delivery metrics — Cycle Time (state segments), Throughput (completed items), WIP (count in
progress), Flow Efficiency (active over active+wait) and Flow Lead Time (created→done) — with the PR
review time and PR size kept as code drill-downs, plus the composed, scope-enforced Fluxo dashboard
endpoint + screen with a coaching-safe throughput×cycle comparison of the node's children. Created
by archiving change grupo-fluxo; re-anchored on the board by change flow-metrics-from-board; volume
cards (Commits, Pull Requests) added by change commits-prs-fluxo.
## Requirements
### Requirement: The Fluxo metrics are available
The system SHALL provide the Fluxo metrics computed by the metrics engine, anchored on the **work
item** (Azure Boards) for the delivery metrics and on the PR/commit only for the code drill-downs:
**Cycle Time** (work-item active-to-terminal time, person-scoped), **Throughput** (count of work
items completed in the period), **WIP** (count of work items in progress), **Flow Efficiency**
(active over active+wait), and **Flow Lead Time** (work item created to completed). It SHALL also
provide the code sub-process drill-downs **PR Review Time** and **PR Size** (median **changed
files**), computed from PR/commit events and clearly presented as diagnostics, not the Fluxo
headline. Each metric SHALL report its value, correct-polarity evolution, and coverage for the
requested node and frequency. Fluxo metrics carry no DORA benchmark tier.

PR Size SHALL be the number of files in the **pull request's own diff** — the same figure the Files
tab shows — and SHALL NOT be the sum of its commits' changed items. Summed that way, a merge commit
inside the pull request dragged in thousands of files from another branch and a file touched five
times counted five, which is how a five-file pull request came to be reported as 2309.

PR Size SHALL be expressed in **changed files** and SHALL NOT be presented as changed lines. Line
counts do not exist in the source: the Azure DevOps Git REST API reports counts of changed *items*
and carries no added/deleted line figure anywhere in a commit. A metric labelled in lines while
counting something else is worse than a missing metric, because a reader has no way to doubt it.

A pull request whose changed-file count is unknown SHALL be excluded from PR Size and SHALL appear
in the metric's openable item list marked as not counted, never given a substitute figure.
Substituting the commit count — which is what produced "3 linhas" for a three-commit pull request —
puts a plausible number in the same field with the same unit, so nothing on screen can reveal that
the measurement is missing.

The reported **coverage** of a median metric measures attribution — events attributed to the node
versus not — and SHALL NOT be read as the share of events that carried a measure. A pull request
that is attributed but has no file count therefore leaves coverage at 100% while being excluded from
the value. This is the long-standing meaning of coverage for every median metric, not something
specific to PR Size, and saying so here is how the two numbers stop contradicting each other on
screen.

#### Scenario: Fluxo metrics computed for a node
- **WHEN** the Fluxo dashboard is requested for a node and frequency
- **THEN** Cycle Time, Throughput, WIP, Flow Efficiency and Flow Lead Time are returned with value and evolution, plus PR Review Time and PR Size as code drill-downs

#### Scenario: Delivery metrics come from the board, not the PR
- **WHEN** Cycle Time, Throughput and Flow Efficiency are computed for a node
- **THEN** they are derived from the node's work items' state history, so work with no code is included and the PR window is not mistaken for the whole cycle

#### Scenario: PR Size matches the Files tab
- **WHEN** a pull request shows five files in the Files tab
- **THEN** PR Size counts five for it, regardless of how many items its commits touched

#### Scenario: PR Size is reported in files
- **WHEN** PR Size is requested for a node
- **THEN** it is the median number of changed files per pull request, labelled in files

#### Scenario: A pull request with no file count is "no data"
- **WHEN** a pull request's changed-file count could not be obtained
- **THEN** it is excluded from the median and listed as not counted, rather than contributing its commit count or a zero

#### Scenario: Coverage still answers attribution, not measurement
- **WHEN** an attributed pull request has no file count
- **THEN** coverage continues to report it as attributed, and the item list is where its exclusion is visible

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

### Requirement: The Fluxo scatter compares structures only
The system SHALL provide a throughput×cycle comparison of the node's children — at the
overview the verticals, within a vertical its teams, and for a team no public
comparison. The system SHALL NOT compare people publicly (individual comparison is
coaching-only). Entries SHALL include only nodes within the caller's access scope.

#### Scenario: Overview scatter compares verticals
- **WHEN** the scatter is requested at the overview node
- **THEN** each vertical appears with its throughput and cycle time, and no people appear

#### Scenario: A team produces no public scatter
- **WHEN** the scatter is requested for a team
- **THEN** no per-person comparison is produced

### Requirement: The Fluxo dashboard is composed and scope-enforced
The system SHALL expose a composed Fluxo dashboard for a node and frequency returning
the Fluxo cards (value, evolution, coverage), the volume cards (Commits and Pull Requests),
the four-phase breakdown, and the throughput×cycle scatter of the node's children, enforcing
the access scope (403 for a node outside scope; individuals coaching-only). The volume cards
SHALL be served at every navigation level — overview, vertical and team — and SHALL be ordered
after the Fluxo metrics so they read as context rather than competing with the delivery
headline. The served Fluxo screen SHALL render this real engine data and match the prototype's
design for the shipped parts at pixel parity, while the numbers reflect the engine.

#### Scenario: Dashboard returned for an in-scope node
- **WHEN** an authenticated user requests the Fluxo dashboard for a node within their scope
- **THEN** the cards, volume cards, phase breakdown and children scatter are returned

#### Scenario: Volume cards served at every level
- **WHEN** the Fluxo dashboard is requested for the overview, for a vertical and for a team
- **THEN** Commits and Pull Requests are present in each of the three responses

#### Scenario: Out-of-scope node denied
- **WHEN** a user requests the Fluxo dashboard for a node outside their scope
- **THEN** the system responds 403

#### Scenario: Fluxo screen chrome matches the prototype
- **WHEN** the Fluxo dashboard is rendered for an admin
- **THEN** its card grid, phase block, ranking and scatter layout match the prototype pixel-for-pixel while the numbers reflect the engine

### Requirement: The Fluxo volume metrics are available
The system SHALL provide two volume metrics computed by the metrics engine over events already
ingested: **Commits** (`commit_count`, count of `COMMIT` events in the period) and **Pull
Requests** (`pr_count`, count of `PR` events in the period). Both SHALL be person-scoped, count
the whole event population of their type in the bucket (no completion filter, so they contrast
with Throughput rather than restate it), and report value, correct-polarity evolution and coverage
for the requested node and frequency. Neither metric SHALL carry a DORA benchmark tier, because
they measure volume of activity, not performance.

#### Scenario: Volume metrics computed for a node
- **WHEN** the Fluxo dashboard is requested for a node and frequency
- **THEN** Commits and Pull Requests are returned with value, evolution and coverage, alongside the existing Fluxo metrics

#### Scenario: Volume counts every event of its type in the period
- **WHEN** a node has commits and PRs in the period whose work items were not completed
- **THEN** those commits and PRs are still counted, so the volume reads differently from Throughput

#### Scenario: Volume metrics carry no benchmark tier
- **WHEN** a Commits or Pull Requests card is rendered
- **THEN** it shows no DORA tier badge

### Requirement: Volume metrics are excluded from the comparison heatmap
The system SHALL NOT include the volume metrics `commit_count` and `pr_count` among the comparison
heatmap's columns. Ranking teams publicly by commit volume is a gameable proxy that contradicts the
product decision to measure the system rather than police people; the volume metrics exist for
context inside a node's own Fluxo view and inside the coaching-only individual panel.

#### Scenario: Heatmap columns unchanged
- **WHEN** the comparison heatmap is requested for any node, frequency and scope
- **THEN** its columns are the DORA, Fluxo and IA metrics as before, with no Commits or Pull Requests column

### Requirement: Volume cards open the standard metric drawer
The system SHALL make the Commits and Pull Requests cards behave exactly like every other metric
card: clicking one SHALL open the metric detail drawer showing the metric's definition, its current
and previous values with evolution, its evolution chart over the last 12 periods built from the
node's real engine series, and the list of items considered in the calculation. Each listed item
SHALL show its own contribution and deep-link to the corresponding commit or pull request in Azure
DevOps.

#### Scenario: Drawer opened from a volume card
- **WHEN** a user clicks the Commits or Pull Requests card
- **THEN** the metric drawer opens with the definition, the evolution chart from real engine data, and the list of items considered

#### Scenario: Listed items deep-link to Azure DevOps
- **WHEN** the drawer lists the commits or pull requests behind a volume metric
- **THEN** each item shows a human-readable description and links to that commit or pull request in Azure DevOps

### Requirement: The Fluxo dashboard responds to the work-type filter
The Fluxo dashboard SHALL accept the work-type selection and apply it to every card it shows: the
board delivery metrics (Cycle Time, Throughput, Flow Lead Time, WIP, Flow Efficiency) by the work
item's own type, and the code drill-downs (PR Review Time, PR Size) plus the volume cards by the
work types of the pull request's linked work items. Each card SHALL state that it was filtered and
SHALL report how many of its events were excluded for carrying no work type.

#### Scenario: Delivery metrics are read by type
- **WHEN** the Fluxo dashboard is requested for a team with the bug type selected
- **THEN** Cycle Time, Throughput, Flow Lead Time, WIP and Flow Efficiency are computed over that team's bug work items only

#### Scenario: Code drill-downs follow the pull request's links
- **WHEN** the same request is made and a pull request is linked only to a feature
- **THEN** that pull request contributes to neither PR Review Time nor PR Size, and is counted among the events excluded by the filter

#### Scenario: Unlinked pull requests are declared
- **WHEN** a filtered Fluxo reading excludes pull requests that carry no work type
- **THEN** the dashboard reports how many, next to the affected cards

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

### Requirement: The Fluxo scatter follows the filter
The throughput × cycle-time comparison of the node's children SHALL be computed under the active
work-type selection, so every point in it answers the same question as the cards above.

#### Scenario: Children are compared under the same filter
- **WHEN** the Fluxo dashboard is read for a vertical with technical debt selected
- **THEN** each team's point is its throughput and cycle time over technical-debt work items

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

