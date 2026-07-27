## ADDED Requirements

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
(`sum`, `median`, `ratio`, or `snapshot`), a unit, a `direction` (whether higher or
lower is better), and a `sentiment` polarity. The catalog SHALL be extensible so that
later metric groups add their own metrics without changing the engine.

#### Scenario: Catalog lists a metric with its computation metadata
- **WHEN** the catalog is read
- **THEN** each metric reports its attribution scope, aggregation, unit, direction and sentiment

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
value at the end of the bucket.

#### Scenario: Team median is computed over the team's events
- **WHEN** a team's median metric is requested and its people have different event counts
- **THEN** the value is the median over all the team's events, not the average of per-person medians

#### Scenario: Ratio is weighted by volume
- **WHEN** a ratio metric is rolled up
- **THEN** it equals the sum of numerators over the sum of denominators across the scope

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
