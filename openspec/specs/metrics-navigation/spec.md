# metrics-navigation Specification

## Purpose
Node-aware metric endpoints (catalog/cards/series) enforcing the authorization
access scope, the navigation shell (frequency + view), and the Tendências view
wired to the metrics engine. Created by archiving change motor-metricas-shell.

## Requirements

### Requirement: Metric endpoints are node-aware and scope-enforced
The system SHALL expose the catalog, per-node metric cards, and per-metric series
through endpoints parameterized by node and frequency, and SHALL enforce the access
scope from authentication/authorization: a request for a node outside the caller's
scope responds 403, and individual (person) data follows the coaching-only rule.

#### Scenario: Cards are returned for an in-scope node
- **WHEN** an authenticated user requests metric cards for a node within their scope
- **THEN** the cards are returned for that node at the requested frequency

#### Scenario: A node outside scope is denied
- **WHEN** an authenticated user requests metrics for a node outside their scope
- **THEN** the system responds 403

#### Scenario: A peer's individual metrics are denied
- **WHEN** a member requests a teammate's individual metric series
- **THEN** the system responds 403

### Requirement: Navigation shell selects frequency and view
The system SHALL provide a navigation shell that lets the user switch frequency
(Diário/Semanal/Mensal), period, and view, recomputing the displayed metrics for the selected
structure node without reloading the structure tree. The shell SHALL reuse the
prototype's design system and be self-contained (no external CDN).

#### Scenario: Changing frequency recomputes the view
- **WHEN** the user switches the frequency selector
- **THEN** the displayed metrics recompute for the new frequency without reloading the tree

#### Scenario: Selecting a node updates the panel
- **WHEN** the user selects a node in the scope-filtered tree
- **THEN** the panel updates to that node's metrics

#### Scenario: Changing period recomputes the view
- **WHEN** the user changes the selected period
- **THEN** the displayed metrics recompute for that period without reloading the tree

### Requirement: Tendências view renders a metric over time
The system SHALL provide a Tendências view that renders a selected metric's series
over the chosen frequency for the current node, matching the prototype's visual
design for the parts this slice ships (shell + Tendências) at pixel parity.

#### Scenario: Tendências shows the series for the current node and frequency
- **WHEN** the user opens the Tendências view for a node
- **THEN** the selected metric's series is charted over time at the current frequency

#### Scenario: Tendências matches the prototype
- **WHEN** the shell and Tendências view are rendered for an admin
- **THEN** they match the prototype's corresponding screens pixel-for-pixel

### Requirement: The viewed period can be moved into the past
The system SHALL let the user choose which period is displayed, at the selected frequency — a day,
a week or a month — and SHALL recompute every displayed number for that period. The control SHALL
offer stepping one period at a time in both directions, jumping to a recent period from a list, and
returning to the current period. The chosen period SHALL be presented in a form that matches the
frequency.

#### Scenario: Opening a past month
- **WHEN** the frequency is monthly and the user selects a past month
- **THEN** every metric on screen is recomputed for that month, and the comparison is against the
  month immediately before it

#### Scenario: The control follows the frequency
- **WHEN** the user switches frequency while a past period is selected
- **THEN** the period control presents the equivalent period at the new frequency — a day, a week,
  or a month — and the displayed numbers follow

#### Scenario: Stepping back and forth
- **WHEN** the user steps one period back and then one period forward
- **THEN** the view returns to the period it started from

#### Scenario: Returning to the present
- **WHEN** the user activates the shortcut back to the current period
- **THEN** the view shows the current period and the control no longer signals a past period

### Requirement: The selected period applies to the whole navigation
When a past period is selected, the system SHALL apply it to every surface that displays metrics —
dashboard cards, trends, the comparison heatmap, the individual panel and metric drilldowns — so
that no two panels on screen describe different intervals. The trend window SHALL end at the
selected period rather than at the present.

#### Scenario: Trends end at the selected period
- **WHEN** a past period is selected and a trend is displayed
- **THEN** the last point of the series is the selected period

#### Scenario: Drilldown matches the card
- **WHEN** the user opens the drilldown of a card while a past period is selected
- **THEN** the listed items are the ones from that same period

#### Scenario: The heatmap follows the period
- **WHEN** a past period is selected and the comparison heatmap is opened
- **THEN** its cells report the values of that period

### Requirement: A viewed period is shareable as a link
The system SHALL carry the selected node, frequency and period in the address, so that opening the
same address later shows the same period. The address SHALL identify the period absolutely, not
relative to the present.

#### Scenario: Reopening a shared address
- **WHEN** an address captured while viewing a past period is opened on a later day
- **THEN** it still shows that same period

#### Scenario: Address without a period
- **WHEN** an address carries no period
- **THEN** the current period is displayed

### Requirement: Missing data is shown as missing, never as zero
The served UI SHALL present the absence of a value explicitly and SHALL NOT display a fabricated
number in its place. This applies whenever the API published no value for that metric and node. A
value the engine actually computed as zero SHALL still be displayed as zero, since that is a
measurement rather than an absence.

#### Scenario: A panel with no published value
- **WHEN** a panel's metric is not published for the current node
- **THEN** the panel states that there is no data, instead of showing zero

#### Scenario: A measured zero is still shown
- **WHEN** the engine computes zero for a metric in the period
- **THEN** the panel displays zero
