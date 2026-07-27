# comparison-heatmap Specification

## Purpose
The cross-cutting Comparativo heatmap over the metrics engine: a node-aware matrix of the
node's children (rows) against every catalog metric across the DORA, Fluxo and IA groups
(columns), each cell the real engine value, with coaching-safe/scope-enforced row selection
(people rows only for the managing/own account) and the served Comparativo screen. Created
by archiving change comparativo-heatmap.

## Requirements

### Requirement: The heatmap compares a node's children across all metrics
The system SHALL provide a comparison heatmap for a node and frequency whose rows are the
node's children and whose columns are every catalog metric across the DORA, Fluxo and IA
groups, in catalog order. Each cell SHALL carry the real engine value of that metric for
the row's node — the same value the composed dashboards report — including the composed AI
impact.

#### Scenario: Heatmap returns a children × all-metrics matrix
- **WHEN** the heatmap is requested for a node and frequency
- **THEN** each child appears as a row with one value per catalog metric in the DORA, then Fluxo, then IA order

#### Scenario: A cell equals the dashboard value
- **WHEN** a metric's value is read for a child from the heatmap and from that child's dashboard card
- **THEN** the two values are equal

### Requirement: Heatmap rows are node-aware
The system SHALL choose the compared rows from the node's position: at the overview the
teams by default or the verticals when the vertical scope is requested; within a vertical
its teams; within a team its people; for a person the person's team colleagues.

#### Scenario: Overview compares teams or verticals
- **WHEN** the heatmap is requested at the overview with the vertical scope
- **THEN** the rows are the verticals; and with the default scope the rows are the teams

#### Scenario: A team compares its people
- **WHEN** the heatmap is requested for a team
- **THEN** the rows are the people of that team

### Requirement: The heatmap is coaching-safe and scope-enforced
The system SHALL enforce the access scope on the heatmap: it SHALL respond 403 when the
base node is outside the caller's scope; it SHALL include only structure rows
(teams/verticals) the caller may view; and it SHALL include a person row only for an admin
or the managing/own account that may view that individual. An org-wide or exec account
SHALL see a team's aggregate but none of its people as rows.

#### Scenario: Out-of-scope base node denied
- **WHEN** a user requests the heatmap for a node outside their scope
- **THEN** the system responds 403

#### Scenario: People rows are coaching-only
- **WHEN** an org-wide account requests a team's heatmap
- **THEN** no person rows are returned, whereas the team's manager receives the team's people as rows

### Requirement: The Comparativo screen renders the real heatmap
The served Comparativo screen SHALL read this endpoint and render the heat table — the
DORA/Fluxo/IA column groups, the teams/verticals toggle at the overview, and the
relative-standing legend — matching the prototype's design at pixel parity, while the
numbers reflect the engine.

#### Scenario: Comparativo chrome matches the prototype
- **WHEN** the Comparativo view is rendered for an admin
- **THEN** its heat table, column groups, scope toggle and legend match the prototype pixel-for-pixel while the numbers reflect the engine
