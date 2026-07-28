# metrics-engine Specification

## Purpose
The raw event model, metric catalog, and on-read aggregation engine: attribution
(person/repo, as-of-event), correct hierarchy roll-up (median/ratio recomputed over
the population, never composed from children), frequency bucketing with
correct-polarity evolution, and unattributed/coverage tracking. Created by archiving
change motor-metricas-shell.

## Requirements

### Requirement: Raw events are stored durably
The system SHALL persist raw activity events durably (PostgreSQL), each with a type
(`COMMIT`, `PR`, `DEPLOY`, `WORKITEM`), an `occurred_at` UTC instant, an optional
`repo_key`, an optional `committer_identity`, an optional numeric value, an optional
phase, an `is_ai` flag, and a type-specific detail payload. Ingestion SHALL happen
through a single outbound port so the source (seed today, Azure DevOps later) can
change without touching the domain or the aggregation engine.

#### Scenario: An ingested event is retrievable
- **WHEN** an event is ingested through the event-store port
- **THEN** it is persisted and available to the aggregation engine on the next read

#### Scenario: Events survive a restart
- **WHEN** the application restarts
- **THEN** previously ingested events are still present (durable, not in memory)

### Requirement: Metric catalog declares how each metric is computed
The system SHALL expose a metric catalog where each metric declares its
`attributionScope` (`person` or `repo`), its `aggregation`
(`sum`, `median`, `ratio`, or `snapshot`), a `measure` naming which per-event value it
reads (`value` for the event's numeric value, or a named detail key such as
`recovery_hours`), a unit, a `direction` (whether higher or lower is better), a
`sentiment` polarity, and optional benchmark tier bands. The catalog SHALL be
extensible so that later metric groups add their own metrics without changing the
engine, and `measure` SHALL default to `value` so existing metrics are unaffected.

#### Scenario: Catalog lists a metric with its computation metadata
- **WHEN** the catalog is read
- **THEN** each metric reports its attribution scope, aggregation, measure, unit, direction and sentiment

#### Scenario: A metric reads the declared measure
- **WHEN** two median metrics over the same event type declare different measures
- **THEN** each aggregates the per-event field it declares, producing independent values

### Requirement: Metrics attribute along one of two paths
The system SHALL compute a `person`-scoped metric by attributing each event through
its committer identity to a Person, and a `repo`-scoped metric by attributing each
event through its repository to a Team. A metric SHALL use exactly the path declared
in the catalog.

#### Scenario: Person-scoped metric attributes via committer identity
- **WHEN** a person-scoped metric is computed for a person
- **THEN** only events whose committer identity maps to that person are counted

#### Scenario: Repo-scoped metric attributes via repository
- **WHEN** a repo-scoped metric is computed for a team
- **THEN** only events whose repository maps to that team are counted

### Requirement: Attribution is as-of-event
The system SHALL attribute each event to the Team and Vertical the Person occupied on
the event's `occurred_at`, resolved from the team-membership history, and SHALL NOT
use the Person's current team when it differs from the historical one. Moving a Person
between teams SHALL NOT change metrics for events that occurred before the move.

#### Scenario: An event counts for the team held at the time
- **WHEN** a person's event occurred while they were in team A, and they later moved to team B
- **THEN** that event is attributed to team A, not team B

#### Scenario: Moving a person does not rewrite history
- **WHEN** a person is moved to a new team
- **THEN** the aggregates of prior periods for the old team are unchanged

### Requirement: Aggregation rolls up the hierarchy correctly
The system SHALL aggregate metrics up the Pessoa→Time→Vertical→overview hierarchy.
For `median` and `ratio` metrics the value at any node SHALL be recomputed over the
full population of events that fall within that node, and SHALL NOT be composed from
children's medians or ratios. `sum` SHALL add the underlying values; `ratio` SHALL be
the sum of numerators divided by the sum of denominators; `snapshot` SHALL take the
value at the end of the bucket. For `median` and `snapshot`, the per-event value used
SHALL be the metric's declared `measure` (the numeric value or a named detail key),
and events lacking that measure SHALL be excluded from that metric's population.

#### Scenario: Team median is computed over the team's events
- **WHEN** a team's median metric is requested and its people have different event counts
- **THEN** the value is the median over all the team's events, not the average of per-person medians

#### Scenario: Ratio is weighted by volume
- **WHEN** a ratio metric is rolled up
- **THEN** it equals the sum of numerators over the sum of denominators across the scope

#### Scenario: A median metric ignores events lacking its measure
- **WHEN** a median metric declares a detail-key measure and some events do not carry that key
- **THEN** those events are excluded from the metric's population rather than counted as zero

### Requirement: Values are bucketed by frequency with correct-polarity evolution
The system SHALL bucket events by Diário, Semanal (ISO week, Monday start), or Mensal
in UTC, and SHALL report each metric's evolution versus the immediately previous
bucket using the metric's `direction`, so that a real improvement reads positive even
when the raw number decreased. When the current bucket is still in progress, the
comparison SHALL use the same elapsed slice of the previous bucket rather than the
previous full bucket.

#### Scenario: Weekly value compares against the prior week
- **WHEN** a metric series is requested at weekly frequency
- **THEN** each bucket reports its value and its evolution versus the previous week

#### Scenario: A lower-is-better metric that falls reads as an improvement
- **WHEN** a metric whose direction is "lower is better" decreases versus the previous period
- **THEN** its evolution is reported as a positive/good change

#### Scenario: Current partial period compares like-for-like
- **WHEN** the current bucket is only partially elapsed
- **THEN** its evolution compares the elapsed slice to the same elapsed slice of the previous bucket

### Requirement: Unattributed events are separated and coverage is reported
The system SHALL place events whose identity or repository does not resolve to a
Person/Team into an "Não atribuído" bucket, excluded from team and vertical
aggregates, and SHALL expose a coverage percentage (attributed events over total) as
a data-quality indicator.

#### Scenario: An unlinkable event does not inflate a team
- **WHEN** an event's committer identity is not linked to any Person
- **THEN** the event is excluded from every team/vertical aggregate and counted as unattributed

#### Scenario: Coverage reflects attribution completeness
- **WHEN** some events are unattributed
- **THEN** the reported coverage percentage is below 100% by the unattributed proportion


### Requirement: Distinct-count ratio aggregation
The metric catalog SHALL support a distinct-count ratio aggregation whose value is the
number of distinct attributed people matching a per-event predicate over the number of
distinct attributed people in the denominator population, for the node and period. It
SHALL attribute along the person path and as-of-event, counting each person once per node
subtree regardless of how many matching events they produced.

#### Scenario: Distinct-count ratio counts people once
- **WHEN** a distinct-count-ratio metric is computed and a person has many matching events in the period
- **THEN** that person contributes one to the numerator and one to the denominator

#### Scenario: Distinct-count ratio rolls up the hierarchy
- **WHEN** the metric is rolled up from teams to a vertical
- **THEN** the vertical value is over the distinct people of the whole subtree, not the average of team ratios

### Requirement: Population split by event attribute
The engine SHALL support evaluating a metric over a subset of the node's population
selected by an event attribute (such as the AI flag), so the same base metric can be
computed over disjoint cohorts of the same node without changing the metric definition.

#### Scenario: Metric computed over an attribute-selected cohort
- **WHEN** a metric is requested with an attribute filter for a node
- **THEN** only events matching the attribute contribute, and the value is computed over that cohort

#### Scenario: Complementary cohorts partition the population
- **WHEN** the same metric is computed for the attribute-present and attribute-absent cohorts
- **THEN** the two cohorts together cover the node's population with the attribute, with no event counted in both


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
