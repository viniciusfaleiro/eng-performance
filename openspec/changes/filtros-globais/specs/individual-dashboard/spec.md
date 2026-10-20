## MODIFIED Requirements

### Requirement: Work is distributed by type with hours
The system SHALL report the person's work distribution by task type — the share and the time per type
(feature, bug, tech debt, maintenance, docs) — from the person's work-item events, where the time per
type is the item's **in-progress time-in-state derived from its change history** (per the
ado-integration mapping), **less the time the item was impeded** (per the impediment-signal
capability), not the manual `CompletedWork` field. Work items with **no usable state transition**
SHALL be excluded from the distribution and reflected as "no data" in the panel's coverage — never
counted as zero.

Impeded time is excluded because the distribution answers where the person's work went, and an item
standing still is not work going anywhere. The **recorded intervals** of an item being worked on
SHALL NOT change for this reason: the impeded stretches are recorded apart, and each reading
subtracts them for itself.

By default the system SHALL count only **leaf work**: a work item that has ingested children is a
container of work rather than work itself, and SHALL NOT contribute time to the distribution. An Epic
SHALL NOT contribute time whether or not it has children, since an Epic is a planning artifact. Every
other item SHALL contribute time when it has no ingested children, regardless of its type, so a team
that works directly in a User Story or a Feature is measured rather than reported as idle.

That default SHALL yield to the reader's **container selection** (per the container-filter
capability) when it asks for containers: a reader who selected "containers only" asked how much time
the items that group work were open, and answering with the leaves would answer a different
question. The selection is the one place that decides which side is being read, rather than each
screen deciding for itself.

Whether an item has children SHALL be determined from the whole ingested corpus, not from the
selected period, so that an item's classification does not change when the viewer changes the window.

Each work type SHALL be openable as the list of items counted under it, and each entry SHALL carry
**both** its elapsed in-progress time in the period and the time actually counted for it after the
concurrent-item division, alongside its title and a link to the work item. One figure alone cannot be
checked: the elapsed time does not explain the total, and the counted time does not explain why an
item that was open for days appears as minutes. Items excluded by the leaf rule SHALL NOT appear in
the list, since the list has to add up to the figure it opens from.

#### Scenario: Type distribution returned
- **WHEN** the individual panel is requested for a person whose work items have recorded state transitions
- **THEN** each work type is returned with its in-progress time and its share of the person's total in-progress time

#### Scenario: Items without transitions are "no data", not zero
- **WHEN** some of the person's work items have no usable state transition
- **THEN** those items are excluded from the distribution and lower its coverage, instead of contributing zero time

#### Scenario: A container does not compete with the work it groups
- **WHEN** a person has a User Story in progress together with the Tasks that hang off it
- **THEN** only the Tasks contribute time, and the User Story contributes none

#### Scenario: An item worked on directly still counts
- **WHEN** a person's User Story or Feature has no ingested children
- **THEN** its in-progress time contributes to the distribution

#### Scenario: A container of any type is excluded
- **WHEN** a Bug has ingested child Tasks
- **THEN** the Bug contributes no time and its Tasks do

#### Scenario: An Epic never contributes
- **WHEN** a person's Epic is in progress and has no ingested children
- **THEN** it still contributes no time

#### Scenario: Classification does not depend on the window
- **WHEN** a User Story's child Tasks all ran outside the selected period
- **THEN** the User Story is still treated as a container and contributes no time

#### Scenario: Opening the items counted under a type
- **WHEN** a user opens the list behind a work type
- **THEN** the items counted under it are listed, each with its elapsed in-progress time, the time counted for it after division, its title and a link to the work item

#### Scenario: The list adds up to the figure it opened from
- **WHEN** the list behind a work type is shown
- **THEN** the counted times in it sum to the hours reported for that type, and no item excluded by the leaf rule appears

#### Scenario: A person with only containers
- **WHEN** every work item attributed to the person in the period is a container
- **THEN** the panel says there is no leaf work in the period rather than presenting an empty distribution as zero

#### Scenario: Impeded time does not count as the person's work
- **WHEN** one of the person's items was impeded during part of its in-progress time in the period
- **THEN** only the time that was not impeded contributes to the distribution

#### Scenario: The distribution follows the container selection
- **WHEN** a reader selects containers only
- **THEN** the distribution reports the hours of the items that group work, instead of the leaves

#### Scenario: Without a selection the leaf rule stands
- **WHEN** no container selection is in force
- **THEN** the distribution counts leaf work exactly as it did before the selection existed
