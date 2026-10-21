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

### Requirement: Pull request ingestion reads the vote history
The system SHALL collect each pull request's reviewer vote history, not only the reviewers' current
votes, so that a vote later replaced by another is still known. When the history of a pull request
cannot be read, the system SHALL treat its review outcome as unknown rather than as an approval.

#### Scenario: A replaced vote is still ingested
- **WHEN** a reviewer voted to reject a pull request and later voted to approve it
- **THEN** the ingested pull request carries the fact that changes were requested

#### Scenario: Unreadable history is not read as approval
- **WHEN** a pull request's vote history cannot be retrieved
- **THEN** the pull request is not recorded as approved without changes

### Requirement: The current revision of a work item is not discarded
When reconstructing a work item's state history, the system SHALL treat the revision that has not
been superseded yet as a real transition, dating it by the work item's own last-changed timestamp.
The system SHALL NOT drop it for carrying the "not yet superseded" marker, since for an item closed
and left alone that revision is the transition to the terminal state, and dropping it leaves the
item with no delivery data at all. A transition SHALL still be dropped when the work item offers no
usable last-changed timestamp, or when its revision date cannot be read at all.

#### Scenario: An item closed and not touched again counts as delivered
- **WHEN** a work item's move to a terminal state is its most recent revision
- **THEN** the item is recorded as completed, dated by its last-changed timestamp

#### Scenario: A superseded terminal transition keeps its own date
- **WHEN** a work item was closed and then edited again, so the closing transition is no longer the
  most recent revision
- **THEN** the completion keeps the date of the closing transition itself

#### Scenario: No usable timestamp leaves the transition out
- **WHEN** the current revision cannot be dated, by its own date or by the item's last-changed
  timestamp
- **THEN** that transition is not used

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

### Requirement: A pull request records how many files it changed
Pull request ingestion SHALL record the number of **files changed** by the pull request, summed over
its commits, so that PR Size measures the size of the change rather than the number of commits.

The count SHALL be obtained by asking Azure DevOps for each commit's change counts explicitly: the
commit list endpoints do not carry them, and the single-commit endpoint returns them only when the
change-count parameter is present. The smallest request that yields the figure SHALL be used — the
returned change list may be truncated while the counts themselves are the true totals, so there is
no reason to transfer the list.

A pull request SHALL be recorded **without** a file count when the count cannot be obtained for any
of its commits, rather than with a partial sum. A sum missing one commit is indistinguishable from a
genuine measurement, and the metric that reads it cannot tell that it is low.

The per-commit request SHALL serve every purpose that needs the commit's full form, so that a commit
needing both its untruncated message and its change counts is fetched once.

#### Scenario: A pull request's file count is recorded
- **WHEN** a pull request's commits are ingested and their change counts are available
- **THEN** the event records the total number of changed files

#### Scenario: A commit whose counts cannot be read leaves the pull request without a count
- **WHEN** the change counts of one of a pull request's commits cannot be obtained
- **THEN** the pull request is ingested with no file count rather than with the sum of the others

#### Scenario: One request per commit
- **WHEN** a commit needs both its full message and its change counts
- **THEN** it is fetched once and both are read from the same response

#### Scenario: A failed count does not fail the sync
- **WHEN** the request for a commit's change counts fails
- **THEN** the sync continues and the pull request is reported without a file count

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

### Requirement: Waiting states are told apart as committed queue or backlog
Within the **wait** category, the system SHALL additionally distinguish a **committed queue** — a
state the item reaches after the team has taken it on, where it waits for the next hand rather than
for a decision — from **backlog**, where it waits to be taken on at all. The distinction SHALL come
from the same centralised, name-based seam the three-way classification already uses, so no board
has to be renamed and no process template is hardcoded.

This refines the wait category; it SHALL NOT change the three-way split itself, nor which phase a
state contributes to in the cycle-time breakdown.

#### Scenario: A queue after commitment is committed
- **WHEN** a work item sits in a state named for readiness, such as "Ready" or "Pronto p/ Prod"
- **THEN** that stretch is classified as committed queue, while still counting as wait time in the phases

#### Scenario: A queue before commitment is backlog
- **WHEN** a work item sits in a state named for refinement, new or backlog
- **THEN** that stretch is classified as backlog

#### Scenario: The cycle-time phases do not change
- **WHEN** the same history is read for the phase breakdown
- **THEN** waiting, active and review hours are exactly what they were before the distinction existed

### Requirement: Work item ingestion records the committed stretch
The ingestion SHALL record, on the work-item event, the stretches during which the item was
**committed and not delivered**: from the first entry into a committed queue or a working state
until the terminal state, **less the stretches the item was impeded** (per the impediment-signal
capability). Backlog time before the commitment and time after the terminal state SHALL NOT be part
of it.

This stretch SHALL be recorded **apart from** the in-progress stretches the individual distribution
reads. The two answer different questions — "what has the team taken on and not finished?" versus
"what was this person working on?" — and one record serving both forced the first to inherit the
second's boundaries, which is how work parked in a delivery queue came to be invisible.

An item still open SHALL leave its committed stretch open-ended, to be closed at the clock of the
reading, as the in-progress stretches already are.

#### Scenario: The committed stretch spans queue and work alike
- **WHEN** a work item goes Refinement → Ready → Doing → Code Review → Pronto p/ Prod → Done
- **THEN** the committed stretch runs from Ready to Done, and the Refinement time is not in it

#### Scenario: Impeded time is taken out of the committed stretch
- **WHEN** a work item was impeded for part of the time between its commitment and its delivery
- **THEN** that part is not in the committed stretch

#### Scenario: An item that never left the backlog has no committed stretch
- **WHEN** a work item only ever sat in refinement or new
- **THEN** it records no committed stretch

#### Scenario: The in-progress stretches are unchanged
- **WHEN** the committed stretch is recorded
- **THEN** the in-progress stretches the individual distribution reads are exactly what they were

### Requirement: A pull request's link is derived, not read from the payload
The ingestion SHALL build a pull request's deep link from what it already knows — the organisation,
the project, the repository and the pull request id — rather than reading it from a link field of
the response. The reviews of that pull request, which have no record of their own in Azure DevOps,
SHALL reuse the same link.

The list endpoint the sync reads does not carry a web link, so depending on one left every pull
request with an empty address and every drill-down row unclickable: the list exists so a team can
check a figure item by item, and an item that does not open cannot be checked. Deriving the address
also makes it the same in every environment, instead of present or absent depending on which
endpoint answered.

#### Scenario: A pull request from the list endpoint still gets a link
- **WHEN** a pull request is ingested from a response that carries no web link
- **THEN** its event records a link addressing that pull request in its repository

#### Scenario: The reviews reuse the pull request's link
- **WHEN** the reviews of that pull request are ingested
- **THEN** each review records the same link, since a review has no address of its own

#### Scenario: Repository and project names with spaces
- **WHEN** the repository or project name contains a space
- **THEN** the link is path-encoded and remains valid

