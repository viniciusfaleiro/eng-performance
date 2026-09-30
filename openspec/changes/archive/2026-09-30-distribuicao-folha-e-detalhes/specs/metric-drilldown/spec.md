## MODIFIED Requirements

### Requirement: Drilldown is reachable from the metric drawer
The served UI SHALL let a user open the item list for the metric currently shown in the drawer,
without leaving the drawer, and SHALL present that list with each item's link and its
counted/not-counted marking. The drawer SHALL also present the metric's explanation as served by the
catalog, rather than a description of its own, so the drawer and the information modal never
disagree about the same metric.

A figure that can be opened SHALL show that it can, rather than relying on the user discovering that
the row is clickable. This applies to the individual panel's delivery figures and to its rows of work
types and reviews.

#### Scenario: Opening the drawer offers the item list
- **WHEN** a user opens a metric's drawer
- **THEN** the UI offers a way to load and view the list of items considered for the current period

#### Scenario: The item list carries links and counted marking
- **WHEN** a user opens the item list from a drawer
- **THEN** the list of considered items is shown with links and counted/not-counted marking

#### Scenario: The drawer explains the metric it is showing
- **WHEN** a user opens a metric's drawer
- **THEN** the drawer presents the catalog's explanation for that metric

#### Scenario: An openable figure looks openable
- **WHEN** a figure or row on the individual panel has an item list behind it
- **THEN** the UI shows an affordance for opening it

