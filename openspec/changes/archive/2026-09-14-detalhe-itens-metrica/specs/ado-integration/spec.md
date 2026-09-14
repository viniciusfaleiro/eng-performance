## MODIFIED Requirements

### Requirement: Azure DevOps activity is mapped to raw events
The system SHALL ingest from the **registered repositories across any organizations** — with **no
single configured org and no PAT** — and map activity to the platform's raw events, populating the
same fields the metric groups consume (phase durations and cycle time, PR first-pass approval,
review decision/comments/author, deploy outcome and recovery, **work-item type and its time-in-state
derived from the item's change history** (not the manual effort field), the AI-assist flag from the
commit convention, and the Azure DevOps deep-link). The deep-link and a human-readable label SHALL
be captured for **every raw event type that has a corresponding Azure DevOps record** — pull request
and commit (already captured), and **work item, deploy (pipeline run) and review**, so the platform
can always point back from a raw event to the record that produced it. A review event's link SHALL
be the link of the pull request it belongs to (there is no standalone review record in Azure
DevOps). For each registered repository the system SHALL fetch its pull requests and commits; for
each distinct `(organization, project)` it SHALL fetch that project's pipeline runs and work items.
A pipeline run SHALL be attributed to the team of its **source repository** and classified as a
production deploy by **that repository's production-stage rule**; a run whose source repository is
not registered SHALL be skipped. Committer identities feed the existing identity mapping.

#### Scenario: Ingestion covers only registered repositories, across orgs
- **WHEN** repositories from two different organizations are registered and a sync runs
- **THEN** activity is fetched for exactly those repositories in both orgs, with no org-wide discovery

#### Scenario: Pull requests map to PR and review events
- **WHEN** a pull request with reviewer votes and comments is fetched
- **THEN** a PR raw event (with phases, cycle time and first-pass flag) and its review events (decision, comments, author) are produced, both carrying the pull request's deep-link

#### Scenario: Pipeline runs map to deploys via their source repository
- **WHEN** a pipeline run's source repository is registered and its stage matches that repository's production rule
- **THEN** a DEPLOY raw event is produced for that repository's team with its outcome, lead timing and the pipeline run's deep-link

#### Scenario: A run from an unregistered repository is skipped
- **WHEN** a pipeline run's source repository is not registered
- **THEN** no deploy event is produced for it

#### Scenario: Work items carry a title and a deep-link
- **WHEN** a work item is fetched during a sync
- **THEN** its raw event carries the item's title and a deep-link to that work item in Azure DevOps
