## MODIFIED Requirements

### Requirement: Drilldown is reachable from the metric drawer
The served UI SHALL let a user open the item list for the metric currently shown in the drawer,
without leaving the drawer, and SHALL present that list with each item's link and its
counted/not-counted marking. The drawer SHALL also present the metric's explanation as served by the
catalog, rather than a description of its own, so the drawer and the information modal never
disagree about the same metric.

A figure that can be opened SHALL show that it can, rather than relying on the user discovering that
the row is clickable. This applies to the individual panel's delivery figures and to its rows of work
types and reviews, and to the waiting segment of the cycle-time phase card.

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

#### Scenario: The waiting phase is openable like a metric
- **WHEN** a user opens the waiting phase from the cycle-time phase card
- **THEN** the drawer shows the waiting metric's explanation and the list of items considered for it

## ADDED Requirements

### Requirement: The waiting metric's drawer presents the impediment breakdown
When the drawer shows the **waiting** metric, it SHALL present the breakdown of that waiting time by
impediment reason **above** the item list, stating the total hours the parts compose — the card
beside it is a median, and the breakdown explains the composition of the population's waiting time,
not a decomposition of that median. It SHALL also state how much of the period's population carries
an impediment signal at all. Without that statement a team that simply never marks
impediments is indistinguishable from a team that is never blocked, and the second reading is the
flattering one.

#### Scenario: The breakdown appears with the item list
- **WHEN** a user opens the waiting metric's drawer
- **THEN** the impediment reasons are shown above the list of items considered in the calculation

#### Scenario: The drawer states the signal's coverage
- **WHEN** the waiting metric's drawer is shown
- **THEN** it states how many of the period's items carry an impediment signal
