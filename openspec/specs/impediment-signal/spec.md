# impediment-signal Specification

## Purpose
TBD - created by archiving change impedimento-como-espera. Update Purpose after archive.
## Requirements
### Requirement: Impediment is recognised from a configurable field or tag
The system SHALL recognise that a work item was impeded from two signals on the item's own change
history: a **field** whose value is neither empty nor "None" (case-insensitive), and a **tag** whose
text starts with a configured **prefix**. Both the field's reference name and the tag prefix SHALL be
configuration, not compiled-in values, because the field is custom per organisation and the prefix is
the team's own wording. An item SHALL be considered impeded during an interval when **either** signal
is in force, and the two signals overlapping SHALL NOT count the time twice.

#### Scenario: The field marks the time as impeded
- **WHEN** the configured field is set to a value other than empty or "None" and later cleared
- **THEN** the interval between the two changes is impeded time

#### Scenario: The tag marks the time as impeded
- **WHEN** a tag starting with the configured prefix is added and later removed
- **THEN** the interval between the two changes is impeded time

#### Scenario: Both signals at once count once
- **WHEN** the field and a matching tag are in force over overlapping intervals
- **THEN** the overlapping time is counted once, not twice

#### Scenario: An empty convention recognises nothing
- **WHEN** neither the field name nor the tag prefix is configured
- **THEN** no time is recognised as impeded and every flow figure is exactly what it was before the signal existed

### Requirement: Impeded time counts as waiting, not as work
Impeded time SHALL be an **overlay** over the state classification, never a replacement for it: the
item's states continue to classify its timeline as waiting, active, review or terminal, and the
portion of an **active or review** interval during which the item was impeded SHALL be moved to
**waiting**. Time already classified as waiting SHALL NOT be moved or counted again — it is already
waiting, and adding the overlay to it would report more hours than the item lived.

A board without a blocked column is the reason this exists: without the overlay an item parked for
days inside an active column is reported as pure work, which inflates Flow Efficiency and misstates
every phase of the cycle time.

#### Scenario: Impeded time inside an active state becomes waiting
- **WHEN** part of an interval in an active state was impeded
- **THEN** that part is reported as waiting time and the active time reports only the remainder

#### Scenario: A review interval entirely impeded reports no review time
- **WHEN** a whole interval in a review state was impeded
- **THEN** all of it is reported as waiting and none as review

#### Scenario: Impediment during a waiting state does not double count
- **WHEN** an item was impeded while already in a waiting state
- **THEN** the waiting time is unchanged, and the hours of the item still sum to its cycle

#### Scenario: The sum of the phases is preserved
- **WHEN** the overlay moves time between phases
- **THEN** the item's waiting, active and review hours still sum to the same total they did before

### Requirement: Impeded time is attributed to the reason the team wrote
The **reason** for an impediment SHALL be the text that follows the configured tag prefix — a tag
`Impedimento:infra` yields the reason `infra`. Reasons SHALL be compared case-insensitively and with
surrounding whitespace ignored, so the same reason written differently is one reason, and SHALL be
reported back in the spelling the team used.

Impeded time that carries **no** reason — the field set with no matching tag — SHALL be attributed to
a single reserved "no reason" bucket rather than discarded or invented, so the time is still visible
as impediment. Reason attribution SHALL cover impeded time wherever it occurred, including time that
was already waiting by state, because the breakdown has to account for the whole waiting figure.

#### Scenario: The reason is the tag's suffix
- **WHEN** a work item carries the tag `Impedimento:infra` while impeded
- **THEN** that impeded time is attributed to the reason `infra`

#### Scenario: The same reason written differently is one reason
- **WHEN** two intervals carry `Impedimento:Infra` and `impedimento: infra`
- **THEN** their time is attributed to a single reason

#### Scenario: Impediment without a reason is still visible
- **WHEN** only the field marks the item as impeded, with no matching tag
- **THEN** the time is attributed to the reserved "no reason" bucket

#### Scenario: Reasons never exceed the waiting total
- **WHEN** the reasons of an item are summed
- **THEN** the sum is at most the item's waiting hours

### Requirement: The signal costs no additional request to Azure DevOps
Reconstructing the impediment timeline SHALL reuse the work item's **change history already
fetched** for the state transitions. The system SHALL NOT issue an extra request per work item, per
field or per tag to read the signal, so enabling the convention does not change the cost of a sync.

#### Scenario: Enabling the convention does not add requests
- **WHEN** a sync runs with the convention configured and again with it empty
- **THEN** the number of requests to Azure DevOps is the same

### Requirement: Impeded time is excluded from a person's work without changing work-in-progress
The time an item spent impeded SHALL NOT count as the person's working time in the individual
panel's distribution, since it is time the item stood still. The intervals that report an item as
**in progress** SHALL be unchanged by the signal: an impeded item is still being worked towards, and
removing it from work-in-progress would answer a different question than the one that metric asks.

#### Scenario: Impeded time leaves the person's working hours
- **WHEN** an item of a person was impeded during part of its in-progress time
- **THEN** the distribution reports only the time that was not impeded

#### Scenario: Work-in-progress is unaffected
- **WHEN** an item is impeded while in progress
- **THEN** it still counts as work in progress for the period

