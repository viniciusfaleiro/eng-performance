# individual-dashboard Specification

## Purpose
The person-level contribution panel over the metrics engine: the commit calendar, PR
assertiveness, delivery trends, code-review contribution (given vs received), the work-type
distribution with hours, and the activity drawer with Azure DevOps deep-links. Coaching-only
(shown to an admin or the managing/own account, never aggregated into any comparison). Created
by archiving change painel-individual; commit and pull-request volume series added by change
commits-prs-fluxo.

## Requirements


### Requirement: The individual panel is coaching-only
The system SHALL serve the individual panel for a person node only to an admin or the
managing/own account that may view that individual, responding 403 otherwise, and SHALL never
aggregate an individual's contribution into any cross-structure ranking or comparison.

#### Scenario: Own or managing account allowed
- **WHEN** an admin, the person, or the person's manager requests the individual panel
- **THEN** the panel is returned

#### Scenario: Non-managing account denied
- **WHEN** an org-wide or exec account requests an individual panel it does not manage
- **THEN** the system responds 403

### Requirement: The contribution calendar counts commits per day
The system SHALL provide the person's commit count per day over the last twelve months, so the
screen can render a GitHub-style contribution map. Counts SHALL come from the person's COMMIT
events regardless of team membership over the period.

#### Scenario: Daily commit counts returned
- **WHEN** the individual panel is requested for a person
- **THEN** a per-day commit count for the trailing twelve months is returned

### Requirement: PR assertiveness is the first-pass approval rate
The system SHALL report PR assertiveness as the share of the person's PRs that were approved without
any reviewer ever asking for changes, over all their PRs, person-scoped and higher-is-better. A PR
SHALL NOT count as first-pass when any reviewer cast a negative vote at any point in the pull
request's history, even if that reviewer later approved it. A PR whose vote history cannot be read
SHALL NOT count as first-pass.

#### Scenario: Assertiveness computed
- **WHEN** the individual panel is requested for a person with some PRs approved first-pass
- **THEN** the assertiveness rate equals first-pass approvals over all their PRs

#### Scenario: A rejection followed by an approval is not first-pass
- **WHEN** a reviewer rejected a pull request and approved it after the author made changes
- **THEN** that pull request does not count as a first-pass approval

#### Scenario: Unknown history is not counted as success
- **WHEN** a pull request's vote history cannot be read
- **THEN** it does not count as a first-pass approval

### Requirement: Delivery trends reuse the person-scoped metrics
The system SHALL include the person's delivery trends — throughput, cycle time, % of commits
with AI, and the period's volume of **commits** and **pull requests** — as the same engine series
computed for that person node, with correct-polarity evolution. The volume series SHALL come from
the same `commit_count` and `pr_count` metrics served on the Fluxo dashboard, so the individual
panel and the team view never disagree on the same person's numbers, and SHALL remain
coaching-only: they are shown inside the individual panel and never aggregated into any public
ranking or comparison.

#### Scenario: Delivery series returned for the person
- **WHEN** the individual panel is requested for a person and frequency
- **THEN** throughput, cycle time, %-with-AI, commits and pull-requests series for that person are returned

#### Scenario: Volume series stay coaching-only
- **WHEN** a person's commit and pull-request volume is computed for the individual panel
- **THEN** it is served only to an admin or the managing/own account and is never added to a cross-structure ranking or comparison

### Requirement: Code-review contribution reports both directions
The system SHALL report the person's code-review contribution: comments made, approvals given
and rejections given (from reviews where the person is the reviewer), and reviews given vs
reviews received (where the person is the reviewed PR's author). A reviewer who asked for changes at
any point SHALL be reported as having given a change request for that pull request, even if they
approved it afterwards.

#### Scenario: Given and received are distinct
- **WHEN** the person reviewed others' PRs and also received reviews on their own PRs
- **THEN** reviews given count the person as reviewer and reviews received count the person as author

#### Scenario: Approvals and rejections split by decision
- **WHEN** the person's reviews include approvals and change-requests
- **THEN** approvals given and rejections given are reported separately

#### Scenario: A change request is not erased by a later approval
- **WHEN** a reviewer asked for changes on a pull request and later approved it
- **THEN** that review counts as a change request given

### Requirement: Work is distributed by type with hours
The system SHALL report the person's work distribution by task type — the share and the time per type
(feature, bug, tech debt, maintenance, docs) — from the person's work-item events, where the time per
type is the item's **in-progress time-in-state derived from its change history** (per the
ado-integration mapping), not the manual `CompletedWork` field. Work items with **no usable state
transition** SHALL be excluded from the distribution and reflected as "no data" in the panel's
coverage — never counted as zero.

#### Scenario: Type distribution returned
- **WHEN** the individual panel is requested for a person whose work items have recorded state transitions
- **THEN** each work type is returned with its in-progress time and its share of the person's total in-progress time

#### Scenario: Items without transitions are "no data", not zero
- **WHEN** some of the person's work items have no usable state transition
- **THEN** those items are excluded from the distribution and lower its coverage, instead of contributing zero time

### Requirement: The activity feed deep-links to Azure DevOps
The system SHALL include the person's most recent commits and PRs, each with its Azure DevOps
link, so the activity drawer can open the item directly in ADO.

#### Scenario: Recent activity carries links
- **WHEN** the individual panel is requested for a person
- **THEN** their recent commits and PRs are returned, each with an Azure DevOps URL

### Requirement: The individual screen renders the real panel
The served individual screen SHALL read this endpoint and render the contribution calendar,
assertiveness gauge, delivery cards/trend, code-review section, work-type distribution and the
activity drawer, matching the prototype's design at pixel parity while the numbers reflect the
engine.

#### Scenario: Individual chrome matches the prototype
- **WHEN** the individual panel is rendered for a person the caller may view
- **THEN** its calendar, gauge, delivery, code-review, distribution and drawer layout match the prototype pixel-for-pixel while the numbers reflect the engine

### Requirement: The individual panel's delivery figures are verifiable item by item
The served UI SHALL let a user open, from each delivery figure of the individual panel, the list of
items considered in that figure for that person and period — the same drilldown the structure
dashboards offer, with the same links back to Azure DevOps and the same marking of items that were
not counted. This SHALL cover at least the person's completed work items, commits, pull requests and
AI-assisted share.

#### Scenario: Opening the items behind a figure
- **WHEN** a user opens the drilldown of a delivery figure on someone's individual panel
- **THEN** the items considered for that person and period are listed

#### Scenario: The listing is about the person shown
- **WHEN** the individual panel of a person is open and a drilldown is requested
- **THEN** the listed items are that person's, regardless of which node the navigation is on

#### Scenario: Selecting a figure and opening its items are different gestures
- **WHEN** a user opens the drilldown of a delivery figure
- **THEN** the figure selected for the panel's chart does not change
