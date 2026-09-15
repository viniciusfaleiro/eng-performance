# metric-explanation Specification

## Purpose
Explains to the reader how every number the platform displays is calculated — the rule applied, the
event it comes from, what is counted, what is deliberately excluded, the statistic used and a
concrete worked example — offered next to each value in the served UI and served from the metric
catalog as the single source, so no surface keeps its own copy of a definition. Created by
archiving change explicacao-de-metricas.

## Requirements

### Requirement: Every displayed number offers an explanation
The served UI SHALL show an information affordance next to every element that presents a metric
value — cards, charts, comparison cells, individual panels, distributions and coverage — and
opening it SHALL present how that number is calculated. The affordance SHALL be visible without
hovering and SHALL be reachable by keyboard.

#### Scenario: A metric card offers its explanation
- **WHEN** a user views a metric card on any dashboard
- **THEN** an information control is visible next to the metric name, and activating it opens the
  explanation for that metric

#### Scenario: A chart offers its explanation
- **WHEN** a user views a trend chart, the comparison heatmap, or a distribution panel
- **THEN** an information control is available and opens an explanation of what the visualisation
  shows and how its series are built

#### Scenario: Explaining is distinct from drilling down
- **WHEN** a user activates the information control on a card
- **THEN** the explanation opens, and the drilldown of the items behind the value remains reachable
  by its own separate gesture

### Requirement: The explanation states the rule, the source and the boundaries
An explanation SHALL state, in business language: the rule used to compute the value, which event
it is derived from, which events are counted, which are deliberately left out, and the statistic
applied. It SHALL also state the period the value covers and that a period keeps the team a person
belonged to at the time of the event.

#### Scenario: Reading an explanation
- **WHEN** a user opens the explanation of a delivery metric
- **THEN** it states the originating event, the population counted, what is excluded, and the
  statistic used to aggregate

#### Scenario: Attribution is explained
- **WHEN** a user opens the explanation of any metric attributed to people
- **THEN** it states that the value stays with the team the person belonged to when the event
  happened

### Requirement: The explanation includes a worked example
An explanation SHALL include a concrete worked example with illustrative numbers, showing the
inputs and the resulting value, so the rule can be understood without reading the definition
abstractly.

#### Scenario: Example of a median metric
- **WHEN** a user opens the explanation of a metric aggregated by median
- **THEN** it shows sample values and the resulting median, making clear that it is not an average

#### Scenario: Example of a ratio metric
- **WHEN** a user opens the explanation of a metric expressed as a percentage
- **THEN** it shows what the numerator and the denominator are, with sample numbers and the
  resulting percentage

### Requirement: Explanations are served from a single source
The system SHALL serve metric explanations from the metric catalog, and every surface that explains
a metric SHALL read from it. No surface SHALL carry its own copy of an explanation.

#### Scenario: The drawer and the information modal agree
- **WHEN** the same metric is explained in the drawer and in the information modal
- **THEN** both present the same text, because both read the catalog

#### Scenario: Explanation travels with the catalog
- **WHEN** a client reads the metric catalog
- **THEN** each entry carries its explanation alongside its key, label, unit and aggregation

### Requirement: No metric ships without an explanation
The system SHALL guarantee that every metric published in the catalog has a complete explanation,
and that no explanation refers to a metric that does not exist. This SHALL be enforced
automatically, so that adding a metric without explaining it fails the build.

#### Scenario: A metric added without explanation
- **WHEN** a new metric is added to the catalog and no explanation is written for it
- **THEN** the build fails identifying the metric that lacks an explanation

#### Scenario: An orphan explanation
- **WHEN** an explanation exists for a key that is not in the catalog
- **THEN** the build fails identifying the orphan explanation
