## ADDED Requirements

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
