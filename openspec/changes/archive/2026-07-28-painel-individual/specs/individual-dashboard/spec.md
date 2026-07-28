## ADDED Requirements

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
The system SHALL report PR assertiveness as the share of the person's PRs approved with no
changes requested (first-pass approvals over all PRs), person-scoped and higher-is-better.

#### Scenario: Assertiveness computed
- **WHEN** the individual panel is requested for a person with some PRs approved first-pass
- **THEN** the assertiveness rate equals first-pass approvals over all their PRs

### Requirement: Delivery trends reuse the person-scoped metrics
The system SHALL include the person's delivery trends — throughput, cycle time and % of commits
with AI — as the same engine series computed for that person node, with correct-polarity
evolution.

#### Scenario: Delivery series returned for the person
- **WHEN** the individual panel is requested for a person and frequency
- **THEN** throughput, cycle time and %-with-AI series for that person are returned

### Requirement: Code-review contribution reports both directions
The system SHALL report the person's code-review contribution: comments made, approvals given
and rejections given (from reviews where the person is the reviewer), and reviews given vs
reviews received (where the person is the reviewed PR's author).

#### Scenario: Given and received are distinct
- **WHEN** the person reviewed others' PRs and also received reviews on their own PRs
- **THEN** reviews given count the person as reviewer and reviews received count the person as author

#### Scenario: Approvals and rejections split by decision
- **WHEN** the person's reviews include approvals and change-requests
- **THEN** approvals given and rejections given are reported separately

### Requirement: Work is distributed by type with hours
The system SHALL report the person's work distribution by task type — the share and the hours per
type (feature, bug, tech debt, maintenance, docs) — from the person's work-item events.

#### Scenario: Type distribution returned
- **WHEN** the individual panel is requested for a person
- **THEN** each work type is returned with its hours and its share of the person's total hours

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
