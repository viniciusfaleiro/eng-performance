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
