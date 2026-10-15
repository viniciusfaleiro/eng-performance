## ADDED Requirements

### Requirement: Drilldown inherits the active work-type filter
The item and people drilldowns SHALL be computed under the same work-type selection as the metric
whose drawer opened them, so an opened list always reconciles with the figure it opened from. The
drilldown SHALL state which work types were in force.

#### Scenario: The list matches the filtered card
- **WHEN** a filtered metric's drawer is opened and its items are listed
- **THEN** the list contains exactly the events of the selected types that produced the value

#### Scenario: A people drilldown is filtered too
- **WHEN** a metric measured in people is drilled down under a selection
- **THEN** each person's contribution is computed over the selected types only

#### Scenario: An unfilterable metric says so in the drawer
- **WHEN** a DORA metric's drawer is opened while a work type is selected
- **THEN** the drawer lists its whole population and states that the metric is not filtered by work type
