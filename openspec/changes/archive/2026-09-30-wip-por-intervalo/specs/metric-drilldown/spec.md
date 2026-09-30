## ADDED Requirements

### Requirement: An interval metric's item list follows the same overlap test
For a metric counted by interval overlap, the openable item list SHALL contain exactly the items the
displayed value counted — decided by the same membership test, not by a second rule that happens to
agree. A list that answers "which items were in progress in this period?" differently from the number
above it turns the audit trail into a second source of truth.

#### Scenario: The list matches the count
- **WHEN** a user opens the list behind WIP for a period
- **THEN** every item listed is one the value counted, and every item the value counted is listed

#### Scenario: An item counted only because it is still open appears in the list
- **WHEN** an item counts because its interval was extended to the reading clock
- **THEN** it appears in the list, with the same reason it was counted
