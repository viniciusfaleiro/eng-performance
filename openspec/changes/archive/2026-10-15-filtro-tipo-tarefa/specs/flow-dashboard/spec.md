## ADDED Requirements

### Requirement: The Fluxo dashboard responds to the work-type filter
The Fluxo dashboard SHALL accept the work-type selection and apply it to every card it shows: the
board delivery metrics (Cycle Time, Throughput, Flow Lead Time, WIP, Flow Efficiency) by the work
item's own type, and the code drill-downs (PR Review Time, PR Size) plus the volume cards by the
work types of the pull request's linked work items. Each card SHALL state that it was filtered and
SHALL report how many of its events were excluded for carrying no work type.

#### Scenario: Delivery metrics are read by type
- **WHEN** the Fluxo dashboard is requested for a team with the bug type selected
- **THEN** Cycle Time, Throughput, Flow Lead Time, WIP and Flow Efficiency are computed over that team's bug work items only

#### Scenario: Code drill-downs follow the pull request's links
- **WHEN** the same request is made and a pull request is linked only to a feature
- **THEN** that pull request contributes to neither PR Review Time nor PR Size, and is counted among the events excluded by the filter

#### Scenario: Unlinked pull requests are declared
- **WHEN** a filtered Fluxo reading excludes pull requests that carry no work type
- **THEN** the dashboard reports how many, next to the affected cards

### Requirement: The cycle-time phases follow the filter
The phase breakdown of Cycle Time (waiting, active, review) SHALL be computed over the same
filtered population as the Cycle Time card, so the phases always add up to the value shown above
them.

#### Scenario: Phases match the filtered card
- **WHEN** a work type is selected
- **THEN** the phases are computed over the work items of that type, consistent with the filtered Cycle Time

### Requirement: The Fluxo scatter follows the filter
The throughput × cycle-time comparison of the node's children SHALL be computed under the active
work-type selection, so every point in it answers the same question as the cards above.

#### Scenario: Children are compared under the same filter
- **WHEN** the Fluxo dashboard is read for a vertical with technical debt selected
- **THEN** each team's point is its throughput and cycle time over technical-debt work items
