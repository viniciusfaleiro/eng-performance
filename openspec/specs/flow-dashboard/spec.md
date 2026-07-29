# flow-dashboard Specification

## Purpose
The Fluxo metric group over the metrics engine: Cycle Time broken into four phases
(coding, pickup, review, deploy), Throughput, WIP, PR Review Time, PR Size, and Flow
Efficiency, plus the composed, scope-enforced Fluxo dashboard endpoint + screen with a
coaching-safe throughput×cycle comparison of the node's children. Created by archiving
change grupo-fluxo.

## Requirements

### Requirement: The Fluxo metrics are available
The system SHALL provide the Fluxo metrics computed by the metrics engine: Cycle Time
(median of the four-phase sum, person-scoped), Throughput, WIP, PR Review Time, PR Size
(median changed lines, person-scoped, lower-is-better), and Flow Efficiency. Each SHALL
report its value, correct-polarity evolution, and coverage for the requested node and
frequency. Fluxo metrics carry no DORA benchmark tier.

#### Scenario: Fluxo metrics computed for a node
- **WHEN** the Fluxo dashboard is requested for a node and frequency
- **THEN** Cycle Time, Throughput, WIP, PR Review Time, PR Size and Flow Efficiency are returned with value and evolution

### Requirement: Cycle Time breaks down into four phases
The system SHALL model a PR's cycle time as four phases — coding, pickup, review, and
deploy. Cycle Time SHALL be the median of the per-PR phase sum. Each phase value at a
node SHALL be the median of that phase over the node's whole PR population (recomputed,
not composed from children); the four phase medians need not sum to the Cycle Time
median.

#### Scenario: Phase breakdown returns four medians
- **WHEN** the Fluxo dashboard is requested for a node
- **THEN** the coding, pickup, review and deploy phase medians for that node are returned

#### Scenario: Team phase is the population median
- **WHEN** a team's phase value is computed and its people have different PR counts
- **THEN** the value is the median of that phase over all the team's PRs, not the average of per-person medians

### Requirement: Flow Efficiency is active over total time
The system SHALL compute Flow Efficiency as active time over cycle time, where active =
coding + review and cycle = coding + pickup + review + deploy. It SHALL be a
volume-weighted ratio (sum of active over sum of cycle) across the node's population and
is higher-is-better.

#### Scenario: Flow Efficiency equals active over cycle
- **WHEN** Flow Efficiency is computed for a node
- **THEN** it equals the summed active hours divided by the summed cycle hours across the node's PRs

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
the Fluxo cards (value, evolution, coverage), the four-phase breakdown, and the
throughput×cycle scatter of the node's children, enforcing the access scope (403 for a
node outside scope; individuals coaching-only). The served Fluxo screen SHALL render
this real engine data and match the prototype's design for the shipped parts at pixel
parity, while the numbers reflect the engine.

#### Scenario: Dashboard returned for an in-scope node
- **WHEN** an authenticated user requests the Fluxo dashboard for a node within their scope
- **THEN** the cards, phase breakdown and children scatter are returned

#### Scenario: Out-of-scope node denied
- **WHEN** a user requests the Fluxo dashboard for a node outside their scope
- **THEN** the system responds 403

#### Scenario: Fluxo screen chrome matches the prototype
- **WHEN** the Fluxo dashboard is rendered for an admin
- **THEN** its card grid, phase block, ranking and scatter layout match the prototype pixel-for-pixel while the numbers reflect the engine

### Requirement: WIP measures time spent in progress states
The system SHALL compute **WIP** as the **median time a work item spends in in-progress states** —
the duration in states classified as in-progress (by Azure DevOps state category, per the
ado-integration mapping) — and no longer from the manual `CompletedWork` effort field. WIP SHALL be
reported in hours, lower-is-better, with correct-polarity evolution and coverage for the requested
node and frequency. Work items with **no usable state transition** SHALL be excluded from the value
and reflected in the coverage as "no data" — they SHALL NOT be counted as zero.

#### Scenario: WIP reflects in-progress duration
- **WHEN** the Fluxo dashboard is requested for a node whose work items have recorded state transitions
- **THEN** WIP is the median in-progress time across those items, in hours, with coverage over the items that had usable history

#### Scenario: Items without transitions do not deflate WIP
- **WHEN** some of a node's work items have no usable state transition
- **THEN** those items are excluded from the WIP value and lower the coverage, rather than being counted as zero WIP
