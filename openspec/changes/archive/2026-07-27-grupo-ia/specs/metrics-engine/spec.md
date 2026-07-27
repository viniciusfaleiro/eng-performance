## ADDED Requirements

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
