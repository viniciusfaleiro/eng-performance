## ADDED Requirements

### Requirement: Work item readings can be filtered by container or leaf
The system SHALL let a reader restrict any work-item reading to **everything**, to **containers
only** — the items that group other ingested work — or to **leaves only**, the work itself. The
selection SHALL default to **everything**, so no figure changes until someone asks for a different
reading.

The distinction exists because a container holds the same work as its children: while an Epic sits
open for three months it is counted beside every User Story and Task under it, and the same work is
added two and three times. Which of the two readings is the right one depends on the question — "how
much is open?" wants leaves, "how many initiatives are running?" wants containers — so the system
offers both rather than deciding.

Whether an item is a container SHALL be decided from the **whole ingested corpus**, not from the
selected period, so an item's classification does not change when the viewer changes the window.

#### Scenario: Everything is the default
- **WHEN** a reading is requested with no container selection
- **THEN** both containers and leaves are counted, exactly as before the filter existed

#### Scenario: Leaves only
- **WHEN** a reader selects leaves only, and a User Story has ingested child Tasks
- **THEN** the Tasks are counted and the User Story is not

#### Scenario: Containers only
- **WHEN** a reader selects containers only
- **THEN** only the items that group ingested work are counted

#### Scenario: Classification does not depend on the window
- **WHEN** a User Story's child Tasks all ran outside the selected period
- **THEN** the User Story is still treated as a container

### Requirement: The container selection is visible and shareable
The selection SHALL be offered beside the work-type filter, SHALL show which of the three readings
is in force, and SHALL travel in the address, so that one link reopens exactly the reading it was
copied from — the same rule the work-type filter already follows.

#### Scenario: The selection is on screen
- **WHEN** a dashboard is shown
- **THEN** the container selection is visible alongside the work-type filter, with the active choice marked

#### Scenario: The selection travels in the link
- **WHEN** a reader selects leaves only and copies the address
- **THEN** reopening that address restores the leaves-only reading

### Requirement: The container selection composes with the other restrictions
The container selection SHALL compose with the work-type filter and with any attribute cohort
already in force, rather than replacing either, so a reading can be "leaf bug work" without the two
selections fighting over the same population.

#### Scenario: Container and work type compose
- **WHEN** leaves only is selected together with the bug work type
- **THEN** only leaf items of bug type are counted

#### Scenario: A metric that cannot be filtered says so
- **WHEN** the selection is active and a metric is not about work items
- **THEN** that metric reports that the selection does not apply to it, rather than silently ignoring it
