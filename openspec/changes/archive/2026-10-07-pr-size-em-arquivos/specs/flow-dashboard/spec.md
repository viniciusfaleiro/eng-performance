## MODIFIED Requirements

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
