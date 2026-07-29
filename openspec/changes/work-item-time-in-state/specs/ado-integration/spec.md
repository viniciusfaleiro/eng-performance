## MODIFIED Requirements

### Requirement: Azure DevOps activity is mapped to raw events
The system SHALL ingest from the **registered repositories across any organizations** — with **no
single configured org and no PAT** — and map activity to the platform's raw events, populating the
same fields the metric groups consume (phase durations and cycle time, PR first-pass approval,
review decision/comments/author, deploy outcome and recovery, **work-item type and its time-in-state
derived from the item's change history** (not the manual effort field), the AI-assist flag from the
commit convention, and the Azure DevOps deep-link). For each registered repository the system SHALL
fetch its pull requests and commits; for each distinct `(organization, project)` it SHALL fetch that
project's pipeline runs and work items. A pipeline run SHALL be attributed to the team of its
**source repository** and classified as a production deploy by **that repository's production-stage
rule**; a run whose source repository is not registered SHALL be skipped. Committer identities feed
the existing identity mapping.

#### Scenario: Ingestion covers only registered repositories, across orgs
- **WHEN** repositories from two different organizations are registered and a sync runs
- **THEN** activity is fetched for exactly those repositories in both orgs, with no org-wide discovery

#### Scenario: Pull requests map to PR and review events
- **WHEN** a pull request with reviewer votes and comments is fetched
- **THEN** a PR raw event (with phases, cycle time and first-pass flag) and its review events (decision, comments, author) are produced

#### Scenario: Pipeline runs map to deploys via their source repository
- **WHEN** a pipeline run's source repository is registered and its stage matches that repository's production rule
- **THEN** a DEPLOY raw event is produced for that repository's team with its outcome and lead timing

#### Scenario: A run from an unregistered repository is skipped
- **WHEN** a pipeline run's source repository is not registered
- **THEN** no deploy event is produced for it

## ADDED Requirements

### Requirement: Work-item effort is derived from state-transition history
The system SHALL derive a work item's effort from its **state-transition history** rather than the
manual `Microsoft.VSTS.Scheduling.CompletedWork` field. For each work item in the incremental delta
(those whose `System.ChangedDate` is at/after the sync watermark), the system SHALL fetch the item's
update history and reconstruct the **time spent in each `System.State`** from the state transitions
(each transition's revised timestamp and before/after value). A state SHALL be classified as
**in-progress** or **terminal** by its Azure DevOps state **category** (`Proposed`, `InProgress`,
`Resolved`, `Completed`, `Removed`) when available, with a name-based fallback — never hardcoded to a
single process template. The work-item raw event SHALL carry the accumulated **in-progress**
duration (and the item type) as its measure. The system MUST NOT fetch the whole backlog: only the
incremental delta's items are queried, one history request per item (the update API has no batch).

#### Scenario: In-progress time is reconstructed from transitions
- **WHEN** a work item moved New → Active → Resolved → Closed with recorded timestamps
- **THEN** its raw event's measure is the total time spent in the in-progress state(s), attributed to the item's type

#### Scenario: Only the incremental delta is queried for history
- **WHEN** an incremental sync runs after a watermark
- **THEN** update history is fetched only for work items changed at/after the watermark, one request per item, not for the whole backlog

#### Scenario: A work item with no usable transition is "no data", not zero
- **WHEN** a work item has no state transition in the fetched history (e.g. created and closed in the same instant, or the watermark cut its history)
- **THEN** it produces no in-progress duration and is reported as "no data" for coverage — never a silent zero
