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

Each reported figure SHALL be openable as the list of reviews behind it, each entry carrying its
decision and a link to the pull request the review was made on — a review has no standalone record in
Azure DevOps, so the pull request is its record. Reviews received SHALL be listed as the pull requests
that were reviewed, not aggregated by reviewer, since naming who rejected whom would turn a coaching
view into a comparison between people.

#### Scenario: Given and received are distinct
- **WHEN** the person reviewed others' PRs and also received reviews on their own PRs
- **THEN** reviews given count the person as reviewer and reviews received count the person as author

#### Scenario: Approvals and rejections split by decision
- **WHEN** the person's reviews include approvals and change-requests
- **THEN** approvals given and rejections given are reported separately

#### Scenario: A change request is not erased by a later approval
- **WHEN** a reviewer asked for changes on a pull request and later approved it
- **THEN** that review counts as a change request given

#### Scenario: Opening the reviews behind a figure
- **WHEN** a user opens the list behind reviews given or reviews received
- **THEN** the reviews counted in that figure are listed, each with its decision and a link to its pull request

#### Scenario: Reviews received are not attributed by reviewer
- **WHEN** the list behind reviews received is shown
- **THEN** it lists the pull requests reviewed rather than ranking or totalling the people who reviewed them

### Requirement: Work is distributed by type with hours
The system SHALL report the person's work distribution by task type — the share and the time per type
(feature, bug, tech debt, maintenance, docs) — from the person's work-item events, where the time per
type is the item's **in-progress time-in-state derived from its change history** (per the
ado-integration mapping), not the manual `CompletedWork` field. Work items with **no usable state
transition** SHALL be excluded from the distribution and reflected as "no data" in the panel's
coverage — never counted as zero.

The system SHALL count only **leaf work**: a work item that has ingested children is a container of
work rather than work itself, and SHALL NOT contribute time to the distribution. An Epic SHALL NOT
contribute time whether or not it has children, since an Epic is a planning artifact. Every other
item SHALL contribute time when it has no ingested children, regardless of its type, so a team that
works directly in a User Story or a Feature is measured rather than reported as idle.

Whether an item has children SHALL be determined from the whole ingested corpus, not from the
selected period, so that an item's classification does not change when the viewer changes the window.

Each work type SHALL be openable as the list of items counted under it, and each entry SHALL carry
**both** its elapsed in-progress time in the period and the time actually counted for it after the
concurrent-item division, alongside its title and a link to the work item. One figure alone cannot be
checked: the elapsed time does not explain the total, and the counted time does not explain why an
item that was open for days appears as minutes. Items excluded by the leaf rule SHALL NOT appear in
the list, since the list has to add up to the figure it opens from.

#### Scenario: Type distribution returned
- **WHEN** the individual panel is requested for a person whose work items have recorded state transitions
- **THEN** each work type is returned with its in-progress time and its share of the person's total in-progress time

#### Scenario: Items without transitions are "no data", not zero
- **WHEN** some of the person's work items have no usable state transition
- **THEN** those items are excluded from the distribution and lower its coverage, instead of contributing zero time

#### Scenario: A container does not compete with the work it groups
- **WHEN** a person has a User Story in progress together with the Tasks that hang off it
- **THEN** only the Tasks contribute time, and the User Story contributes none

#### Scenario: An item worked on directly still counts
- **WHEN** a person's User Story or Feature has no ingested children
- **THEN** its in-progress time contributes to the distribution

#### Scenario: A container of any type is excluded
- **WHEN** a Bug has ingested child Tasks
- **THEN** the Bug contributes no time and its Tasks do

#### Scenario: An Epic never contributes
- **WHEN** a person's Epic is in progress and has no ingested children
- **THEN** it still contributes no time

#### Scenario: Classification does not depend on the window
- **WHEN** a User Story's child Tasks all ran outside the selected period
- **THEN** the User Story is still treated as a container and contributes no time

#### Scenario: Opening the items counted under a type
- **WHEN** a user opens the list behind a work type
- **THEN** the items counted under it are listed, each with its elapsed in-progress time, the time counted for it after division, its title and a link to the work item

#### Scenario: The list adds up to the figure it opened from
- **WHEN** the list behind a work type is shown
- **THEN** the counted times in it sum to the hours reported for that type, and no item excluded by the leaf rule appears

#### Scenario: A person with only containers
- **WHEN** every work item attributed to the person in the period is a container
- **THEN** the panel says there is no leaf work in the period rather than presenting an empty distribution as zero

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

### Requirement: The individual panel reports platform access apart from delivery
The individual panel SHALL report when that person last signed in to the platform, presented as
platform access and visually separated from the delivery figures, so it is not read as part of the
person's engineering performance. It SHALL NOT be ranked, compared between people, or charted over
time. When the person has no account, the panel SHALL omit it rather than assert anything.

#### Scenario: A person with an account
- **WHEN** the individual panel is shown for a person whose account has signed in
- **THEN** the panel reports when that person last signed in, outside the delivery figures

#### Scenario: A person who never signed in
- **WHEN** the person's account has never signed in
- **THEN** the panel says so explicitly

#### Scenario: A person with no account
- **WHEN** the person has no account on the platform
- **THEN** the panel omits platform access entirely

### Requirement: The panel states what the reported hours are
The individual panel SHALL state that the hours reported in the work distribution are **elapsed**
hours during which an item was in progress, not hours a person worked, and that the hours of a single
item are **divided among the items in progress at the same time**. Without both statements a total is
read as effort spent and a per-item figure is read as wrong.

For an item that has not reached a terminal state, the elapsed hours SHALL run to the **clock of the
reading**, not the clock of the ingestion that recorded the item. Ending at the ingestion clock made
an open item's hours stop at the last sync, so the same item was in progress for two different
lengths depending on which figure of the panel was being read.

#### Scenario: The nature of the total is stated
- **WHEN** the work distribution is shown with a total in hours
- **THEN** the panel states that these are elapsed hours in progress, not hours worked

#### Scenario: The division is stated where per-item hours appear
- **WHEN** a list of items with their hours is shown
- **THEN** it states that concurrent items share each elapsed hour

#### Scenario: An open item's hours reach the moment of reading
- **WHEN** an item is still in progress and the last ingestion ran earlier
- **THEN** its elapsed hours run to the moment of the reading, not to the last ingestion

### Requirement: An item in progress for only minutes is flagged as a convention break
The system SHALL flag, through the panel's existing convention mechanism, work items whose total
in-progress time is implausibly short for real work, as a sign that the board was updated after the
fact rather than during it. The flag SHALL identify the items so the manager can check them, and
SHALL be presented as a convention to agree on, never as a judgement of the person.

#### Scenario: A card moved after the work was done
- **WHEN** a work item was in progress for only a few minutes
- **THEN** the panel flags it as a possible convention break, identifying the item

#### Scenario: Normal items are not flagged
- **WHEN** a person's work items were in progress for plausible durations
- **THEN** no such flag is raised

#### Scenario: The flag survives the leaf rule
- **WHEN** a short-lived item is leaf work and therefore contributes its minutes to the distribution
- **THEN** it is both counted and flagged, so a near-zero share is explained rather than merely shown
