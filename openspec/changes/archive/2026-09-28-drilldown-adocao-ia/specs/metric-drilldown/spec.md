## ADDED Requirements

### Requirement: A metric measured in people drills down to people
When a metric counts distinct people rather than events, the system SHALL present its drilldown as a
listing of those people, not of the underlying events. Metrics that count events SHALL keep listing
events. The served UI SHALL decide which form to show from the metric's own definition, so a new
metric of either kind needs no separate registration.

#### Scenario: Drilling down a person-counting metric
- **WHEN** a user opens the drilldown of a metric whose unit is the person
- **THEN** the listing identifies people, not the events they produced

#### Scenario: Event-counting metrics are unchanged
- **WHEN** a user opens the drilldown of a metric that counts events
- **THEN** the listing identifies the events considered, as before
