## ADDED Requirements

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
The system SHALL fetch Repos/PRs/commits, Pipelines and Boards from Azure DevOps and map them to
the platform's raw events, populating the same fields the metric groups consume (phase durations
and cycle time, PR first-pass approval, review decision/comments/author, deploy outcome and
recovery, work-item type and hours, the AI-assist flag from the commit convention, and the
Azure DevOps deep-link). Committer identities and repository keys SHALL feed the existing
identity and repository mapping.

#### Scenario: Pull requests map to PR and review events
- **WHEN** a pull request with reviewer votes and comments is fetched
- **THEN** a PR raw event (with phases, cycle time and first-pass flag) and its review events (decision, comments, author) are produced

#### Scenario: Pipeline runs map to deploys under the production-stage rule
- **WHEN** a pipeline run for the configured production stage is fetched
- **THEN** a DEPLOY raw event is produced with its outcome and lead/recovery timing

#### Scenario: Unmapped identities lower coverage, not the load
- **WHEN** a committer identity is not yet linked to a person
- **THEN** its events still load and are reported as unattributed via the existing coverage badge

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
