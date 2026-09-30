## ADDED Requirements

### Requirement: A work item records which item is its parent
Work item ingestion SHALL record the identity of the item's parent, in addition to the parent's type
it already records. Without it the system cannot answer whether an item has children, and cannot tell
a container of work apart from work.

The parent's own accessibility SHALL NOT be required: the relationship is recorded on the child, so a
parent that was deleted or lies outside the ingested scope costs nothing.

#### Scenario: An item with a parent
- **WHEN** a work item has a parent in Azure DevOps
- **THEN** the ingested event records which item that parent is

#### Scenario: An item with no parent
- **WHEN** a work item has no parent
- **THEN** the ingested event records no parent and remains valid

#### Scenario: An inaccessible parent does not fail the item
- **WHEN** a work item's parent cannot be read
- **THEN** the item is still ingested with the parent's identity recorded
