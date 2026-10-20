## MODIFIED Requirements

### Requirement: Impeded time is excluded from a person's work without changing work-in-progress
The time an item spent impeded SHALL NOT count as the person's working time in the individual
panel's distribution, since it is time the item stood still. It SHALL NOT count as work in progress
either: work-in-progress measures what the team has taken on and not delivered, and a blocked item
is not being delivered towards while it is blocked.

The **recorded intervals** of an item being worked on SHALL remain untouched by the signal — the
impeded stretches are recorded apart, and each reading subtracts them for itself. That is what keeps
one reading's decision from silently becoming another's: the individual distribution subtracts them
from the hours it prorates, and work-in-progress reads the committed stretch, which already excludes
them.

#### Scenario: Impeded time leaves the person's working hours
- **WHEN** an item of a person was impeded during part of its in-progress time
- **THEN** the distribution reports only the time that was not impeded

#### Scenario: Impeded time leaves work-in-progress
- **WHEN** an item is impeded for a whole period
- **THEN** it is not counted as work in progress for that period

#### Scenario: The recorded intervals are unchanged
- **WHEN** the impediment signal is read for an item
- **THEN** the intervals recorded for the item being worked on are exactly what they were, and the impeded stretches are recorded apart from them
