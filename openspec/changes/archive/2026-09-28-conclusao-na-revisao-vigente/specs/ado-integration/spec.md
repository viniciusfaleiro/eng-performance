## ADDED Requirements

### Requirement: The current revision of a work item is not discarded
When reconstructing a work item's state history, the system SHALL treat the revision that has not
been superseded yet as a real transition, dating it by the work item's own last-changed timestamp.
The system SHALL NOT drop it for carrying the "not yet superseded" marker, since for an item closed
and left alone that revision is the transition to the terminal state, and dropping it leaves the
item with no delivery data at all. A transition SHALL still be dropped when the work item offers no
usable last-changed timestamp, or when its revision date cannot be read at all.

#### Scenario: An item closed and not touched again counts as delivered
- **WHEN** a work item's move to a terminal state is its most recent revision
- **THEN** the item is recorded as completed, dated by its last-changed timestamp

#### Scenario: A superseded terminal transition keeps its own date
- **WHEN** a work item was closed and then edited again, so the closing transition is no longer the
  most recent revision
- **THEN** the completion keeps the date of the closing transition itself

#### Scenario: No usable timestamp leaves the transition out
- **WHEN** the current revision cannot be dated, by its own date or by the item's last-changed
  timestamp
- **THEN** that transition is not used
