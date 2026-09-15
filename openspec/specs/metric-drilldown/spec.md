# metric-drilldown Specification

## Purpose
Lets a user drill from any metric's displayed value down to the exact raw events behind it — the
same attribution and population the engine used to compute that value — each item linked back to
its Azure DevOps record when available, reachable from the metric drawer in the served UI. Created
by archiving change detalhe-itens-metrica.

## Requirements

### Requirement: Drilldown lists the exact items behind a metric's value
For any metric in the catalog, node, frequency and period, the system SHALL list the raw events
that were considered by that metric's aggregation for that node in that period — the same
attribution and population the engine already uses to compute the displayed value, never a
separate recomputation that could diverge from it.

#### Scenario: Sum-based metric lists every matching event
- **WHEN** a user requests the item list for a `SUM` metric (e.g. throughput) at a node and period
- **THEN** the list contains every event attributed to that node in that period that fed the count

#### Scenario: Median-based metric marks items without the measure as not counted
- **WHEN** a user requests the item list for a `MEDIAN` metric (e.g. cycle time) and some matching
  events lack the measure the metric reads
- **THEN** those events appear in the list marked as not counted, with the reason, instead of being
  silently omitted

#### Scenario: Snapshot-based metric marks only the latest event per entity as counted
- **WHEN** a user requests the item list for a `SNAPSHOT` metric (e.g. WIP) and an entity has more
  than one matching event in the period
- **THEN** only that entity's most recent event is marked counted; the earlier ones for the same
  entity appear marked as not counted, with the reason

### Requirement: Each item links to its Azure DevOps record
Each item in the drilldown list SHALL carry, when available, a link to the corresponding Azure
DevOps record (pull request, work item, pipeline run) and a human-readable label (title or
summary). An item whose source event has no captured link SHALL still appear in the list, without a
link.

#### Scenario: Item with a captured link opens the real record
- **WHEN** a drilldown item's underlying event has a URL captured at ingestion
- **THEN** the item exposes that URL so the UI can open the Azure DevOps record directly

#### Scenario: Item without a captured link still appears
- **WHEN** a drilldown item's underlying event has no URL captured
- **THEN** the item still appears in the list, with its label but no link

### Requirement: Drilldown endpoint is node-aware and scope-enforced
The system SHALL expose the drilldown list through an endpoint parameterized by metric key, node,
frequency and an optional period (defaulting to the current period), and SHALL enforce the same
access scope as the other metric endpoints: a request for a node outside the caller's scope
responds 403.

#### Scenario: Default period is the current one
- **WHEN** a drilldown request omits the period
- **THEN** the system returns the items for the most recent (current) period of that node/frequency

#### Scenario: Request for a node outside scope is rejected
- **WHEN** a drilldown request targets a node outside the caller's access scope
- **THEN** the system responds 403

### Requirement: Drilldown is reachable from the metric drawer
The served UI SHALL let a user open the item list for the metric currently shown in the drawer,
without leaving the drawer. The drawer SHALL also present the metric's explanation as served by the
catalog, rather than a description of its own, so the drawer and the information modal never
disagree about the same metric.

#### Scenario: Opening the drawer offers the item list
- **WHEN** a user opens a metric's drawer
- **THEN** the UI offers a way to load and view the list of items considered for the current period

#### Scenario: The drawer explains the metric it is showing
- **WHEN** a user opens a metric's drawer
- **THEN** the drawer presents the catalog's explanation for that metric
