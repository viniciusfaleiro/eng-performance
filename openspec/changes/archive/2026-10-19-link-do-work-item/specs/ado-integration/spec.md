## ADDED Requirements

### Requirement: A work item's link and project come from the item itself
The ingestion SHALL read a work item's **own project** from the item and SHALL NOT infer it from the
project being synchronised when the event is built. The item's project SHALL be recorded on the
event, so that "which project is this item in?" is answerable from the data rather than from the
shape of the sync loop.

The deep link SHALL address the work item in the form the Azure DevOps organisation resolves by
**id**, so that the link opens the right item regardless of which project it belongs to. A project
SHALL appear in the link only when it is the item's own.

This matters because the batch read of work items is organisation-scoped: it returns any id it is
given, from any project, without complaint. Stamping those items with the project the loop happened
to be on produced links that opened the right item under the wrong name — and a drill-down exists
precisely so a team can check whether to believe a number.

#### Scenario: The item's own project is recorded
- **WHEN** a work item is ingested
- **THEN** the event records the project the item belongs to, as reported by the item

#### Scenario: An item from another project is not relabelled
- **WHEN** the batch read returns a work item that belongs to a project other than the one being synchronised
- **THEN** the event records that item's own project, not the one being synchronised

#### Scenario: The link opens the item regardless of project
- **WHEN** a reader opens a work item from a drill-down list
- **THEN** the link opens that work item in Azure DevOps

#### Scenario: No extra request is made for the project
- **WHEN** the project of each item is read
- **THEN** it comes from the batch read already performed, with no additional request per item
