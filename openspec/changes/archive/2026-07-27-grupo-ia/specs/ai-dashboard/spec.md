## ADDED Requirements

### Requirement: The IA metrics are available
The system SHALL provide the IA metrics computed by the metrics engine: AI Share (% of
commits marked AI-assisted, reused), AI Adoption (% of the node's active people with at
least one AI-assisted commit in the period), and AI Impact (the relative cycle-time
difference between AI-assisted and non-AI PRs). Each SHALL report its value,
correct-polarity evolution, and coverage for the requested node and frequency. IA metrics
carry no DORA benchmark tier.

#### Scenario: IA metrics computed for a node
- **WHEN** the IA dashboard is requested for a node and frequency
- **THEN** AI Share, AI Adoption and AI Impact are returned with value and evolution

### Requirement: AI Adoption counts distinct people
The system SHALL compute AI Adoption as the number of distinct people with at least one
AI-assisted commit in the period over the number of distinct people with at least one
commit in the period, attributed along the person path and as-of-event so a person is
counted under their team-of-record and only once per node subtree. It is higher-is-better.

#### Scenario: Adoption is a distinct-people ratio
- **WHEN** a person makes several AI-assisted commits in the period
- **THEN** they count once in both the AI-adopters and the active-people totals

#### Scenario: Adoption rolls up without double-counting a mover
- **WHEN** a person changed teams mid-period and committed in each
- **THEN** they count once under the team-of-record for each event, not twice at the vertical

### Requirement: AI Impact compares AI and non-AI PRs
The system SHALL compute AI Impact by evaluating cycle time over two cohorts of the node's
PRs — those marked AI-assisted and those not — and reporting how much faster the
AI-assisted cohort is. Coverage SHALL reflect how much of the population carries an AI
mark.

#### Scenario: Impact splits the population by the AI flag
- **WHEN** AI Impact is computed for a node whose PRs are a mix of AI and non-AI
- **THEN** cycle time is computed separately for the AI cohort and the non-AI cohort and the relative difference is returned

#### Scenario: One cohort empty yields no impact
- **WHEN** a node has PRs in only one cohort
- **THEN** AI Impact reports no comparison rather than a misleading value

### Requirement: The IA comparison compares structures only
The system SHALL provide an AI-adoption ranking of the node's children — at the overview
the verticals, within a vertical its teams, and for a team no public comparison — and the
with/without-AI panels over the node's population. The system SHALL NOT compare people
publicly (individual comparison is coaching-only). Ranking and panel entries SHALL include
only nodes within the caller's access scope.

#### Scenario: Overview ranking compares verticals
- **WHEN** the adoption ranking is requested at the overview node
- **THEN** each vertical appears with its adoption, and no people appear

#### Scenario: A team produces no public ranking
- **WHEN** the ranking is requested for a team
- **THEN** no per-person comparison is produced

### Requirement: The IA dashboard is composed and scope-enforced
The system SHALL expose a composed IA dashboard for a node and frequency returning the IA
cards (value, evolution, coverage), the %-with-AI trend, the adoption ranking of the
node's children, the with/without-AI donut, and the AI-vs-non-AI cycle-time series,
enforcing the access scope (403 for a node outside scope; individuals coaching-only). The
served IA screen SHALL render this real engine data and match the prototype's design for
the shipped parts at pixel parity, while the numbers reflect the engine.

#### Scenario: Dashboard returned for an in-scope node
- **WHEN** an authenticated user requests the IA dashboard for a node within their scope
- **THEN** the cards, %-with-AI trend, adoption ranking, donut and AI-vs-non-AI series are returned

#### Scenario: Out-of-scope node denied
- **WHEN** a user requests the IA dashboard for a node outside their scope
- **THEN** the system responds 403

#### Scenario: IA screen chrome matches the prototype
- **WHEN** the IA dashboard is rendered for an admin
- **THEN** its card grid, trend, adoption ranking, donut and comparison series layout match the prototype pixel-for-pixel while the numbers reflect the engine
