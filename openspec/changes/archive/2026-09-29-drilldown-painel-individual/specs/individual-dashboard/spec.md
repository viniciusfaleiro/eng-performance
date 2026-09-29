## ADDED Requirements

### Requirement: The individual panel's delivery figures are verifiable item by item
The served UI SHALL let a user open, from each delivery figure of the individual panel, the list of
items considered in that figure for that person and period — the same drilldown the structure
dashboards offer, with the same links back to Azure DevOps and the same marking of items that were
not counted. This SHALL cover at least the person's completed work items, commits, pull requests and
AI-assisted share.

#### Scenario: Opening the items behind a figure
- **WHEN** a user opens the drilldown of a delivery figure on someone's individual panel
- **THEN** the items considered for that person and period are listed

#### Scenario: The listing is about the person shown
- **WHEN** the individual panel of a person is open and a drilldown is requested
- **THEN** the listed items are that person's, regardless of which node the navigation is on

#### Scenario: Selecting a figure and opening its items are different gestures
- **WHEN** a user opens the drilldown of a delivery figure
- **THEN** the figure selected for the panel's chart does not change
