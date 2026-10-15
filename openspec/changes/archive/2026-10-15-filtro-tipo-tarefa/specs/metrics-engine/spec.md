## ADDED Requirements

### Requirement: Population can be restricted by work type
The engine SHALL support restricting a metric's population to a set of work types, composing that
restriction with any attribute cohort already in force (such as the AI flag), so a metric can be
read for, say, the AI cohort of bug work. An empty set SHALL mean no restriction.

#### Scenario: Work type and cohort compose
- **WHEN** a metric is requested for the AI cohort with the bug type selected
- **THEN** only AI-flagged bug events contribute, and the value is computed over that population

#### Scenario: An empty selection changes nothing
- **WHEN** a metric is requested with no work type selected
- **THEN** the result is identical to the same request before the filter existed

### Requirement: Events excluded for lack of work type are reported apart
When a work-type restriction is in force, the engine SHALL count the events that were attributed to
the node but carry no work type, and SHALL report that count alongside the metric, separately from
coverage.

#### Scenario: Untyped events are counted, not hidden
- **WHEN** a filtered metric is computed and some attributed events carry no work type
- **THEN** the result reports how many were excluded for that reason

## MODIFIED Requirements

### Requirement: Unattributed events are separated and coverage is reported
The system SHALL place events whose identity or repository does not resolve to a
Person/Team into an "Não atribuído" bucket, excluded from team and vertical
aggregates, and SHALL expose a coverage percentage (attributed events over total) as
a data-quality indicator.

When a work-type restriction is in force, coverage SHALL be computed over the **filtered**
population: attributed events of the selected types over all events of the selected types. An event
of another type SHALL NOT count as uncovered — it is outside the question being asked — and an event
excluded for carrying no work type SHALL NOT count as uncovered either, being reported in its own
figure instead.

#### Scenario: An unlinkable event does not inflate a team
- **WHEN** an event's committer identity is not linked to any Person
- **THEN** the event is excluded from every team/vertical aggregate and counted as unattributed

#### Scenario: Coverage reflects attribution completeness
- **WHEN** some events are unattributed
- **THEN** the reported coverage percentage is below 100% by the unattributed proportion

#### Scenario: Coverage under a filter measures the filtered denominator
- **WHEN** a metric is read with a work type selected
- **THEN** coverage is attributed events of that type over all events of that type, and events of other types do not lower it
