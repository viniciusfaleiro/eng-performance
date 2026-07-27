## ADDED Requirements

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
(Diário/Semanal/Mensal) and view, recomputing the displayed metrics for the selected
structure node without reloading the structure tree. The shell SHALL reuse the
prototype's design system and be self-contained (no external CDN).

#### Scenario: Changing frequency recomputes the view
- **WHEN** the user switches the frequency selector
- **THEN** the displayed metrics recompute for the new frequency without reloading the tree

#### Scenario: Selecting a node updates the panel
- **WHEN** the user selects a node in the scope-filtered tree
- **THEN** the panel updates to that node's metrics

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
