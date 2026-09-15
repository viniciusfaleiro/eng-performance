## MODIFIED Requirements

### Requirement: Drilldown is reachable from the metric drawer
The served UI SHALL let a user open the item list for the metric currently shown in the drawer,
without leaving the drawer. The drawer SHALL also present the metric's explanation as served by the
catalog, rather than a description of its own, so the drawer and the information modal never
disagree about the same metric.

#### Scenario: Opening the drawer offers the item list
- **WHEN** a user opens a metric's drawer
- **THEN** the UI offers a way to load and view the list of items considered for the current period

#### Scenario: The drawer explains the metric it is showing
- **WHEN** a user opens a metric's drawer
- **THEN** the drawer presents the catalog's explanation for that metric
