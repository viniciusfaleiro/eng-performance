## ADDED Requirements

### Requirement: Waiting states are told apart as committed queue or backlog
Within the **wait** category, the system SHALL additionally distinguish a **committed queue** — a
state the item reaches after the team has taken it on, where it waits for the next hand rather than
for a decision — from **backlog**, where it waits to be taken on at all. The distinction SHALL come
from the same centralised, name-based seam the three-way classification already uses, so no board
has to be renamed and no process template is hardcoded.

This refines the wait category; it SHALL NOT change the three-way split itself, nor which phase a
state contributes to in the cycle-time breakdown.

#### Scenario: A queue after commitment is committed
- **WHEN** a work item sits in a state named for readiness, such as "Ready" or "Pronto p/ Prod"
- **THEN** that stretch is classified as committed queue, while still counting as wait time in the phases

#### Scenario: A queue before commitment is backlog
- **WHEN** a work item sits in a state named for refinement, new or backlog
- **THEN** that stretch is classified as backlog

#### Scenario: The cycle-time phases do not change
- **WHEN** the same history is read for the phase breakdown
- **THEN** waiting, active and review hours are exactly what they were before the distinction existed

### Requirement: Work item ingestion records the committed stretch
The ingestion SHALL record, on the work-item event, the stretches during which the item was
**committed and not delivered**: from the first entry into a committed queue or a working state
until the terminal state, **less the stretches the item was impeded** (per the impediment-signal
capability). Backlog time before the commitment and time after the terminal state SHALL NOT be part
of it.

This stretch SHALL be recorded **apart from** the in-progress stretches the individual distribution
reads. The two answer different questions — "what has the team taken on and not finished?" versus
"what was this person working on?" — and one record serving both forced the first to inherit the
second's boundaries, which is how work parked in a delivery queue came to be invisible.

An item still open SHALL leave its committed stretch open-ended, to be closed at the clock of the
reading, as the in-progress stretches already are.

#### Scenario: The committed stretch spans queue and work alike
- **WHEN** a work item goes Refinement → Ready → Doing → Code Review → Pronto p/ Prod → Done
- **THEN** the committed stretch runs from Ready to Done, and the Refinement time is not in it

#### Scenario: Impeded time is taken out of the committed stretch
- **WHEN** a work item was impeded for part of the time between its commitment and its delivery
- **THEN** that part is not in the committed stretch

#### Scenario: An item that never left the backlog has no committed stretch
- **WHEN** a work item only ever sat in refinement or new
- **THEN** it records no committed stretch

#### Scenario: The in-progress stretches are unchanged
- **WHEN** the committed stretch is recorded
- **THEN** the in-progress stretches the individual distribution reads are exactly what they were
