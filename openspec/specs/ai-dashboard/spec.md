# ai-dashboard Specification

## Purpose
The IA metric group over the metrics engine: AI share (% of commits AI-assisted), AI
adoption (% of a node's people who used AI), and AI impact (cycle time of AI vs non-AI
PRs), plus the composed, scope-enforced IA dashboard endpoint + screen with a
coaching-safe AI-adoption comparison of the node's children. Created by archiving change
grupo-ia.

## Requirements

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
the verticals, within a vertical its teams, and within a team the people of that team — and the
with/without-AI panels over the node's population. The system SHALL NOT produce a ranking at the
person level, and SHALL NOT compare people from different teams. A person SHALL appear in a ranking
only to a caller allowed to view that person individually, so the comparison stays the manager's
coaching over their own team rather than a public league table.

#### Scenario: Overview ranking compares verticals
- **WHEN** the adoption ranking is requested at the overview node
- **THEN** each vertical appears with its adoption, and no people appear

#### Scenario: A team ranks its own people
- **WHEN** the ranking is requested for a team by a caller who may view those people individually
- **THEN** the people of that team appear with their adoption

#### Scenario: People outside the caller's individual scope are withheld
- **WHEN** the ranking is requested for a team by a caller who may see the team but not its people
  individually
- **THEN** those people are not included in the ranking

#### Scenario: A person produces no ranking
- **WHEN** the ranking is requested for a person
- **THEN** no ranking is produced, since that would compare them with their peers

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

### Requirement: AI adoption can be broken down by person
The system SHALL let a user open the AI-adoption figure and see the people behind it, ranked by how
much of their work was AI-assisted — both those who used it most and those who used it least. The
breakdown SHALL cover the population of the node currently being viewed: the whole organisation at
the overview, a vertical's people within a vertical, a team's people within a team.

#### Scenario: Breaking down adoption at a team
- **WHEN** a user opens the adoption breakdown while viewing a team
- **THEN** the people of that team are listed, ranked by their share of AI-assisted work

#### Scenario: Breaking down adoption at the overview
- **WHEN** a user opens the adoption breakdown at the overview
- **THEN** the listing covers the people of the whole organisation, within the caller's scope

#### Scenario: Both ends are reported
- **WHEN** the adoption breakdown is returned
- **THEN** it identifies both the people with the highest and the people with the lowest AI-assisted
  share

### Requirement: The adoption breakdown counts only people who produced code
The breakdown SHALL list only people with at least one commit in the period — the same population
the adoption ratio divides by. A person with no commits in the period SHALL NOT be reported as a
non-user of AI, since not producing code in a period is not evidence about AI at all.

#### Scenario: Someone who did not commit is absent
- **WHEN** a person made no commits in the period
- **THEN** they appear in neither end of the breakdown

#### Scenario: Someone who committed without AI is a non-user
- **WHEN** a person committed in the period and none of their commits is AI-assisted
- **THEN** they appear among the people with the lowest AI-assisted share

### Requirement: The adoption breakdown shows what each person contributed
Each person in the breakdown SHALL be reported with the numbers behind their position — their
AI-assisted commits and their total commits in the period — so the reader can tell a low share on
little work from a low share on much work.

#### Scenario: Counts accompany each person
- **WHEN** the breakdown lists a person
- **THEN** it reports their AI-assisted commit count and their total commit count for the period

### Requirement: The adoption breakdown respects individual access
A person SHALL appear in the breakdown only to a caller allowed to view that person individually.
The aggregate figure SHALL remain unchanged by this filtering, so the number and the list may cover
different populations.

#### Scenario: A manager sees only their own people
- **WHEN** a manager opens the adoption breakdown at a node covering people they may not view
  individually
- **THEN** only the people they may view individually are listed

#### Scenario: Filtering does not alter the figure
- **WHEN** the listing is filtered by access
- **THEN** the adoption percentage still reflects the whole population of the node
