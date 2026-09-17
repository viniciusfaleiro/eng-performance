# ado-integration Specification

## Purpose
Interactive (device-code) Azure DevOps ingestion — no PAT, no stored secret: authenticate as
the admin via Microsoft Entra, fetch Repos/PRs/commits, Pipelines and Boards and map them to
the platform's raw events (behind the existing store port), classify production deploys via a
stage rule, and run an admin-triggered async sync (backfill + incremental watermark) with a
progress UI. Created by archiving change adapter-ado-real.

## Requirements

### Requirement: Ingestion authenticates interactively with no PAT
The system SHALL authenticate to Azure DevOps using an interactive Microsoft Entra device-code
flow — a public client and the Azure DevOps scope — with no Personal Access Token, no stored
secret, and no custom app registration. It SHALL surface the user-code and verification URL to
the admin, acquire the user's token after they complete login and MFA, and hold that token only
in memory for the duration of the sync.

#### Scenario: Device-code prompt is surfaced
- **WHEN** an admin starts a sync
- **THEN** the system returns a user-code and verification URL for the admin to complete login with MFA on Microsoft's page

#### Scenario: No credential is persisted
- **WHEN** a sync completes or fails
- **THEN** no Azure DevOps token, secret or PAT is stored; only the sync watermark and summary remain

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

### Requirement: Work-item effort is derived from state-transition history
The system SHALL derive a work item's flow measures from its **state-transition history** rather than
the manual `Microsoft.VSTS.Scheduling.CompletedWork` field. For each work item in the incremental
delta (those whose `System.ChangedDate` is at/after the sync watermark), the system SHALL fetch the
item's update history and reconstruct the **time spent in each `System.State`** from the state
transitions (each transition's revised timestamp and before/after value). Each state SHALL be
classified into **one of three categories** — **active**, **wait** (blocked/idle) or **terminal** —
by its Azure DevOps state **category** (`Proposed`, `InProgress`, `Resolved`, `Completed`, `Removed`)
when available, with a **configurable name-based fallback** (e.g. `Blocked`, `On Hold`, `Waiting`,
`Ready for…` → wait) — never hardcoded to a single process template. From this the work-item raw
event SHALL carry: the item **type**, the **active** duration, the **wait** duration, the **cycle**
(first active state to terminal), the **creation** and **completion** timestamps, and a
**completed** marker when a terminal state was reached. The system MUST NOT fetch the whole backlog:
only the incremental delta's items are queried, one history request per item (the update API has no
batch).

#### Scenario: Active, wait and cycle reconstructed from transitions
- **WHEN** a work item moved New → Active → Blocked → Active → Resolved → Closed with recorded timestamps
- **THEN** its raw event carries the summed active time, the summed wait time, the cycle from the first Active to Closed, and the completion timestamp, attributed to the item's type

#### Scenario: State categories drive the active/wait/terminal split
- **WHEN** a state exposes an Azure DevOps state category
- **THEN** it is classified as active, wait or terminal by that category, falling back to the configurable name heuristic only when the category is unavailable

#### Scenario: Only the incremental delta is queried for history
- **WHEN** an incremental sync runs after a watermark
- **THEN** update history is fetched only for work items changed at/after the watermark, one request per item, not for the whole backlog

#### Scenario: A work item with no usable transition is "no data", not zero
- **WHEN** a work item has no state transition in the fetched history (e.g. created and closed in the same instant, or the watermark cut its history)
- **THEN** it produces no active/wait/cycle measure and is reported as "no data" for coverage — never a silent zero

### Requirement: Sync is admin-triggered, incremental and idempotent
The system SHALL run the load as an admin-triggered background job: a first run backfills a
configurable window (6–12 months) and later runs fetch only activity newer than a per-source
watermark. Events SHALL upsert by identity so re-running a sync does not duplicate them, and the
job SHALL report progress and a last-sync summary.

#### Scenario: First run backfills, later runs fetch the diff
- **WHEN** the first sync runs and then a second sync runs later
- **THEN** the first loads the whole backfill window and the second loads only activity after the recorded watermark

#### Scenario: Re-running does not duplicate
- **WHEN** the same activity is fetched by two syncs
- **THEN** it results in one raw event, not duplicates

### Requirement: The Admin screen drives and reports the sync
The served Admin → Integração ADO screen SHALL let an admin start a sync, show the device-code
prompt, display live progress (per-source counts and phase), and show the last-sync time and
coverage. Read views SHALL reflect the loaded data with no further action.

#### Scenario: Admin runs a sync from the screen
- **WHEN** an admin clicks Sincronizar and completes login
- **THEN** the screen shows progress and, on completion, the last-sync summary, and the dashboards read the loaded events

#### Scenario: Only admins may sync
- **WHEN** a non-admin account calls the sync endpoint
- **THEN** the system denies it

### Requirement: One unreachable source does not abort the sync
When collecting from a registered repository or from a project fails, the system SHALL record the
failure and continue with the remaining sources, rather than aborting the whole sync. Only a failure
that prevents any collection at all — authentication not completed, or no repository registered —
SHALL abort it.

#### Scenario: A repository that cannot be read
- **WHEN** one registered repository answers with an error and the others answer normally
- **THEN** the events of the other repositories are ingested and the sync completes

#### Scenario: A project that cannot be read
- **WHEN** fetching a project's pipeline runs or work items fails
- **THEN** the other projects are still collected and the sync completes

#### Scenario: A failure that prevents all collection still aborts
- **WHEN** authentication is not completed, or no repository is registered
- **THEN** the sync fails, rather than reporting a completion with no data

### Requirement: The sync reports which sources failed
A sync that finished with failures SHALL report the list of sources that failed — identifying each
repository or project and the reason — alongside the count of ingested events, and SHALL make clear
that the run completed with failures rather than cleanly.

#### Scenario: Failures are listed at the end
- **WHEN** a sync finishes with one or more sources failed
- **THEN** its result lists each failed source with the reason reported by Azure DevOps

#### Scenario: A clean run reports no failures
- **WHEN** every source is collected successfully
- **THEN** the result reports the sync as completed with no failed sources

### Requirement: A partial sync does not advance the watermark
When a sync finished with failures, the system SHALL keep the previous watermark, so the same window
is collected again on the next run and the gap left by the failed source does not become permanent.
Ingesting the same event twice SHALL NOT duplicate it.

#### Scenario: Window is retried after a partial run
- **WHEN** a sync completes with a failed repository
- **THEN** the watermark is unchanged, and the next sync collects the same window again

#### Scenario: Re-ingesting is idempotent
- **WHEN** the same event is ingested by two consecutive syncs
- **THEN** it exists once, with the values from the later ingestion

#### Scenario: A clean run advances the watermark
- **WHEN** a sync completes with no failures
- **THEN** the watermark advances to the most recent ingested event
