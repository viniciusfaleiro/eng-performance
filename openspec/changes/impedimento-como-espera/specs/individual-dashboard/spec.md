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
standing still is not work going anywhere. The intervals that report an item as **in progress**
SHALL NOT change for this reason, so work-in-progress keeps counting an impeded item.

The system SHALL count only **leaf work**: a work item that has ingested children is a container of
work rather than work itself, and SHALL NOT contribute time to the distribution. An Epic SHALL NOT
contribute time whether or not it has children, since an Epic is a planning artifact. Every other
item SHALL contribute time when it has no ingested children, regardless of its type, so a team that
works directly in a User Story or a Feature is measured rather than reported as idle.

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

### Requirement: The panel states what the reported hours are
The individual panel SHALL state that the hours reported in the work distribution are **elapsed**
hours during which an item was in progress, not hours a person worked, and that the hours of a single
item are **divided among the items in progress at the same time**. Without both statements a total is
read as effort spent and a per-item figure is read as wrong. It SHALL also state that **time the item
was impeded does not count**, since that is what makes the same item report fewer hours here than the
window it was open.

For an item that has not reached a terminal state, the elapsed hours SHALL run to the **clock of the
reading**, not the clock of the ingestion that recorded the item. Ending at the ingestion clock made
an open item's hours stop at the last sync, so the same item was in progress for two different
lengths depending on which figure of the panel was being read.

#### Scenario: The nature of the total is stated
- **WHEN** the work distribution is shown with a total in hours
- **THEN** the panel states that these are elapsed hours in progress, not hours worked

#### Scenario: The division is stated where per-item hours appear
- **WHEN** a list of items with their hours is shown
- **THEN** it states that concurrent items share each elapsed hour

#### Scenario: An open item's hours reach the moment of reading
- **WHEN** an item is still in progress and the last ingestion ran earlier
- **THEN** its elapsed hours run to the moment of the reading, not to the last ingestion

#### Scenario: The exclusion of impeded time is stated
- **WHEN** the work distribution is shown
- **THEN** the panel states that time the item was impeded is not counted as work
