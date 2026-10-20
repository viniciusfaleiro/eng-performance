## MODIFIED Requirements

### Requirement: Impeded time is attributed to the reason the team wrote
The **reason** for an impediment SHALL be the text that follows the configured tag prefix — a tag
`Impedimento:infra` yields the reason `infra`. Reasons SHALL be compared case-insensitively and with
surrounding whitespace ignored, so the same reason written differently is one reason, and SHALL be
reported back in the spelling the team used.

Impeded time that carries **no** reason — the field set with no matching tag — SHALL be attributed to
a single reserved "no reason" bucket rather than discarded or invented, so the time is still visible
as impediment. Reason attribution SHALL cover impeded time wherever it occurred, including time that
was already waiting by state, because the breakdown has to account for the whole waiting figure.

A stretch covered by **more than one reason at the same time** SHALL be **split equally** among
them: ten hours blocked under two reasons are five hours each, not ten hours twice. Attributing the
whole stretch to each reason made the reasons add up to more than the item ever waited, and the
excess was then hidden by clamping the residual at zero — so the breakdown looked consistent while
being wrong. The invariant SHALL hold instead: **the reasons of an item sum to its impeded time**.

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

#### Scenario: Two reasons at once split the stretch
- **WHEN** an item carries two impediment tags over the same ten hours
- **THEN** each reason receives five hours

#### Scenario: Partly overlapping reasons split only the overlap
- **WHEN** one reason covers ten hours and a second covers the last four of them
- **THEN** the first receives eight hours and the second two, summing to the ten hours impeded
