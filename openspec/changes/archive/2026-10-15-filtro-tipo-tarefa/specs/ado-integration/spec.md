## ADDED Requirements

### Requirement: A pull request records the work items it is linked to
Ingestion SHALL read the work items linked to each pull request and SHALL record, on the pull
request event, the set of work-type categories of those items. The commits and review events
derived from that pull request SHALL carry the same set, so code-side metrics can be read by work
type. A pull request linked to no work item SHALL carry no work type at all — never a default
category.

#### Scenario: A pull request takes the categories of its items
- **WHEN** a pull request is linked to a bug and to a user story
- **THEN** its event records both bug and feature work, and so do its commits and reviews

#### Scenario: A pull request with no link carries no type
- **WHEN** a pull request is linked to no work item
- **THEN** its event records no work type, and it is excluded from filtered readings rather than defaulted

#### Scenario: Links are resolved in batches
- **WHEN** a sync ingests many pull requests
- **THEN** the work items behind them are resolved in batches, reusing the sync's existing work-item fetch, never one full work-item request per pull request

### Requirement: Pull request work types are backfilled
The sync SHALL be able to re-ingest already-stored pull requests to fill in their work-item links,
so history becomes readable by work type instead of only new pull requests being filterable. Until
a source has been re-ingested, its older pull requests SHALL count as carrying no work type.

#### Scenario: History becomes filterable after a backfill
- **WHEN** an administrator runs the backfill for a source
- **THEN** its existing pull request events gain the work types of their linked items, with no duplicate events created

## MODIFIED Requirements

### Requirement: Work items are classified into work types by a specified mapping
The system SHALL classify each work item into one of the platform's work types from its Azure DevOps
work item type, by a **single declared mapping table** — the same table read by ingestion, by the
unmapped-type report and by the Admin screen that displays it. The table SHALL map: a bug to bug; a
user story, a feature, a product backlog item or an epic to feature work; a type named for
technical debt to technical debt; a type named for maintenance, chore or support to maintenance; a
type named for documentation or other to documentation/other. Any type not covered SHALL be
classified as documentation/other **and marked as unmapped**, so the fallback stays visible instead
of silently absorbing types nobody noticed. Matching SHALL ignore letter case, so a team's own
naming does not change the result.

#### Scenario: Standard types are classified
- **WHEN** a work item is a bug, a user story, a feature or a product backlog item
- **THEN** it is classified as a bug for the first and as feature work for the others

#### Scenario: An epic is feature work
- **WHEN** a work item is an epic
- **THEN** it is classified as feature work

#### Scenario: A team's custom types are recognised
- **WHEN** a work item's type is the team's technical-debt, maintenance or documentation type
- **THEN** it is classified as technical debt, maintenance or documentation/other accordingly

#### Scenario: An unknown type falls back and is marked
- **WHEN** a work item's type matches no entry in the table
- **THEN** it is classified as documentation/other, marked as unmapped, and its raw type is reported with a count

#### Scenario: The mapping has one definition
- **WHEN** ingestion classifies an item and the Admin screen lists how types map
- **THEN** both are derived from the same declared table
