## ADDED Requirements

### Requirement: The panel's item lists are paged like every other
The individual panel's own lists SHALL follow the same paging as every other list of considered
items (per the metric-drilldown capability) — ten at a time, with the position and the total in
view. That covers the items counted under a work type and the pull requests behind a review figure.

Where the panel already **caps** the list it serves, the cap SHALL be stated where the navigation
is, rather than only in a sentence above the rows. A reader who pages to the end of a capped list
and finds the hours not adding up will conclude the figure is wrong, when what is missing is the
rest of the list.

#### Scenario: The work-type item list is paged
- **WHEN** a work type with 30 counted items is opened
- **THEN** ten are shown at a time, with the total of 30 in view

#### Scenario: A capped work-type list is honest about it
- **WHEN** the list of a work type was capped before it was served
- **THEN** the navigation states that the list is partial and ends where the served items end

#### Scenario: The reviews list is paged
- **WHEN** the reviews behind a figure are opened and there are more than ten
- **THEN** they are paged ten at a time
