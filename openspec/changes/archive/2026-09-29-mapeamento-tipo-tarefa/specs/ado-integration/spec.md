## ADDED Requirements

### Requirement: Work items are classified into work types by a specified mapping
The system SHALL classify each work item into one of the platform's work types from its Azure DevOps
work item type, by this mapping: a bug is a bug; a user story, a feature or a product backlog item
is feature work; an epic is feature work; a work item type named for technical debt is technical
debt; a work item type named for documentation or other is documentation/other; and any type not
covered is documentation/other. Matching SHALL ignore letter case, so a team's own naming does not
change the result.

#### Scenario: Standard types are classified
- **WHEN** a work item is a bug, a user story, a feature or a product backlog item
- **THEN** it is classified as a bug for the first and as feature work for the others

#### Scenario: An epic is feature work
- **WHEN** a work item is an epic
- **THEN** it is classified as feature work

#### Scenario: A team's custom types are recognised
- **WHEN** a work item's type is the team's technical-debt or documentation type
- **THEN** it is classified as technical debt or documentation/other accordingly

#### Scenario: An unknown type falls back
- **WHEN** a work item's type matches none of the above
- **THEN** it is classified as documentation/other

### Requirement: A task inherits the work type of its parent
A task SHALL be classified by the work type of the work item it belongs to, since a task carries no
nature of its own — the same task is feature work under a user story and technical debt under a
debt item. The system SHALL look up only one level: a task whose parent is itself a task, or whose
parent cannot be resolved, SHALL fall back to documentation/other rather than climbing further.

#### Scenario: A task under a user story is feature work
- **WHEN** a task belongs to a user story
- **THEN** it is classified as feature work

#### Scenario: A task under a debt item is technical debt
- **WHEN** a task belongs to the team's technical-debt item
- **THEN** it is classified as technical debt

#### Scenario: A task with no resolvable parent falls back
- **WHEN** a task has no parent, or its parent cannot be read
- **THEN** it is classified as documentation/other

#### Scenario: Only one level is climbed
- **WHEN** a task's parent is itself a task
- **THEN** it is classified as documentation/other, without looking up the grandparent

### Requirement: Parent types are resolved in batches, never per task
The system SHALL resolve the types of the parents it needs in batched requests, reusing any parent
already collected in the same run, and SHALL NOT issue one request per task. A failure to resolve
parents SHALL be recorded and treated as nothing resolved, leaving the affected tasks on the
fallback, rather than failing the ingestion.

#### Scenario: A parent already collected costs nothing
- **WHEN** a task's parent was itself collected in the same run
- **THEN** its type is reused with no additional request

#### Scenario: Tasks sharing a parent cost one lookup
- **WHEN** several tasks share the same unresolved parent
- **THEN** that parent's type is requested once, not once per task

#### Scenario: A failed lookup does not fail the sync
- **WHEN** resolving parent types fails
- **THEN** the affected tasks fall back to documentation/other and the ingestion completes
