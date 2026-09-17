## ADDED Requirements

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
