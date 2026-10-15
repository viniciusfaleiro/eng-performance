# work-type-filter Specification

## Purpose
TBD - created by archiving change filtro-tipo-tarefa. Update Purpose after archive.
## Requirements
### Requirement: Work type is a reading dimension with five categories
The system SHALL expose the **work type** as a dimension for reading metrics, with exactly five
categories: feature work, bug, technical debt, maintenance and documentation/other. The set SHALL
be the same one the individual panel's work distribution already uses, so one concept does not get
two vocabularies.

#### Scenario: The five categories are offered
- **WHEN** a reader opens the work-type filter
- **THEN** the five categories are offered, and nothing else

#### Scenario: No selection means everything
- **WHEN** no work type is selected
- **THEN** every event contributes, exactly as before the filter existed

### Requirement: The filter restricts the population before aggregation
When one or more work types are selected, the system SHALL restrict a filterable metric's
population to the events of those types **before** computing the statistic, so medians, ratios and
counts are recalculated over the filtered population and never composed from unfiltered values.

#### Scenario: A median is recomputed, not re-weighted
- **WHEN** Cycle Time is read for a node with the bug type selected
- **THEN** the value is the median over that node's bug work items, not a combination of per-type medians

#### Scenario: Several types select their union
- **WHEN** bug and technical debt are both selected
- **THEN** the population is the events of either type, aggregated together as one population

### Requirement: Only non-deploy metrics are filterable
A metric SHALL be filterable by work type if and only if it is computed from events that can carry
a work type — work item, pull request, commit and review events. Metrics computed from **deploy**
events SHALL NOT be filterable: a deploy belongs to a repository and pipeline and carries work of
several types at once, so a per-type deploy figure would answer a question the data cannot answer.
This covers the four DORA metrics, which are the only deploy-based metrics.

#### Scenario: DORA ignores the filter
- **WHEN** a work type is selected and a DORA metric is read
- **THEN** the metric is computed over its whole population and is reported as not filtered

#### Scenario: Filterability is reported per metric
- **WHEN** any metric is returned under an active filter
- **THEN** the response states whether that metric was filtered

### Requirement: Azure DevOps types map to the five categories by a declared table
The mapping from a raw Azure DevOps work item type to a category SHALL be declared in a single
place, SHALL be the same mapping used by ingestion, by the unmapped-type report and by anything
that displays it, and SHALL be readable by an administrator. Matching SHALL ignore letter case.

#### Scenario: One table, one answer
- **WHEN** ingestion classifies an item and the Admin screen shows how types are mapped
- **THEN** both read the same declared table, with no second copy to keep in sync

#### Scenario: An administrator can see the mapping
- **WHEN** an administrator opens the Azure DevOps integration screen
- **THEN** the type-to-category table is shown, together with the raw types seen in the last sync that matched no entry

### Requirement: An unmapped Azure DevOps type is visible as unmapped
A work item whose raw type matches no entry in the table SHALL still be classified as
documentation/other — so the distribution keeps its shape — but SHALL be marked as **unmapped**, and
the platform SHALL report those raw types and their counts. Documentation/other SHALL NOT be the
silent destination of types nobody noticed.

#### Scenario: An unrecognised type is counted and named
- **WHEN** a sync ingests work items of a type that matches no entry
- **THEN** the type and how many items carried it are reported, and the items are marked unmapped

#### Scenario: Real documentation is distinguishable from the fallback
- **WHEN** documentation/other is read with the unmapped marking available
- **THEN** items mapped there explicitly can be told apart from items that only fell through

### Requirement: Events with no work type are excluded and counted
An event that carries no work type SHALL be excluded from the population under an active filter,
and SHALL be counted in a figure of its own, returned by the API and shown on screen — typically a
pull request linked to no work item. It SHALL NOT be assigned a default category, and it SHALL
NOT be reported as an attribution failure.

#### Scenario: A pull request with no linked work item drops out
- **WHEN** a work type is selected and a pull request is linked to no work item
- **THEN** it contributes to no metric and is counted among the events excluded for lack of type

#### Scenario: The reader is told what was dropped
- **WHEN** a filtered reading excludes events for lack of type
- **THEN** the screen states how many, so an empty or thin chart is explained rather than mysterious

### Requirement: The filter travels as a repeated parameter and is validated
Metric endpoints SHALL accept the work type as a repeatable query parameter, absent meaning no
filter. A value that is not one of the five categories SHALL be rejected with 400 and SHALL NOT be
silently ignored: an ignored filter returns correct numbers for a question the reader did not ask.

#### Scenario: Several types are requested
- **WHEN** a request carries the parameter twice with two valid categories
- **THEN** both are applied as one selection

#### Scenario: An unknown category is rejected
- **WHEN** a request carries a work type outside the five categories
- **THEN** the request fails with 400 and no data is returned

