## MODIFIED Requirements

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
