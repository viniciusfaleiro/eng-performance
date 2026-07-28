## ADDED Requirements

### Requirement: Review events are part of the raw model
The raw event model SHALL include a REVIEW event type: a reviewer's action on a pull request,
carrying the reviewer (as the event's committer identity), the decision (approved or
changes-requested), a comment count, and the reviewed PR's author identity. REVIEW events SHALL
attribute along the person path so a review can be counted for the reviewer (given) or for the
author (received).

#### Scenario: A review attributes to its reviewer
- **WHEN** a REVIEW event's reviewer identity resolves to a person
- **THEN** it is attributable to that person as a review given

#### Scenario: A review attributes to the reviewed author
- **WHEN** a REVIEW event's author identity resolves to a person
- **THEN** it is attributable to that person as a review received
