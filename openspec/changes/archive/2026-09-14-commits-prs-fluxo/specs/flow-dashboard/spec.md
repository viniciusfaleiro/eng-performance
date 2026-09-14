## ADDED Requirements

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

## MODIFIED Requirements

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
