## ADDED Requirements

### Requirement: The container selection applies to every work-item reading
The selection SHALL reach **every** reading that counts work items — dashboards, comparison matrix,
individual panel and any drill-down — and not only the screens it was wired into first. A reading
that counts work items and does not respond is indistinguishable, on screen, from a reading where
the selection had no effect, and the reader has no way to tell which.

An endpoint that receives the selection SHALL either apply it or report that it does not apply.
**Silently discarding it is forbidden**, including by simply not declaring the parameter: an unknown
value SHALL be refused with the same error wherever it is sent, so the same question never gets two
different answers depending on the screen.

#### Scenario: The comparison matrix responds
- **WHEN** the matrix is read with leaves only and a node has containers among its work items
- **THEN** the values of the filterable columns are computed over the leaves alone

#### Scenario: The individual panel responds
- **WHEN** a person's panel is read with leaves only
- **THEN** their delivery figures are computed over the leaves alone

#### Scenario: An unknown value is refused everywhere
- **WHEN** an unknown container selection reaches any endpoint that accepts the selection
- **THEN** it is rejected with the same error, not accepted and ignored
