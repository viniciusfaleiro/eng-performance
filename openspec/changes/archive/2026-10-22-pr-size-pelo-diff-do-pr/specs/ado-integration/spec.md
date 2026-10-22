## MODIFIED Requirements

### Requirement: A pull request records how many files it changed
Pull request ingestion SHALL record the number of **files changed** by the pull request, taken from
the pull request's **own diff** — the difference between the target branch and the pull request's
**latest iteration**, which is what the Files tab shows — so that PR Size measures the change the
pull request proposes.

It SHALL NOT be summed over the pull request's commits. That sum inflated the figure by two
independent routes: a merge commit inside the pull request carries every file coming from the other
branch, thousands of them, none of which is that pull request's work; and a file touched in five
commits counted five times. A pull request with five files in the Files tab was reported as 2309.

Only **files** SHALL be counted: entries describing a folder are not files, and a rename is one
file however the change type is spelled.

The diff SHALL be read in full when it arrives in pages, so a long one is counted whole rather than
cut at the first page.

A pull request SHALL be recorded **without** a file count when the diff cannot be obtained —
no iteration, or a request that fails — rather than with a partial count or any substitute. A
partial count is indistinguishable from a genuine measurement, and the metric that reads it cannot
tell that it is low. A failure SHALL be logged with the pull request's id and SHALL NOT fail the
sync.

Reading the diff SHALL NOT remove the per-commit request that serves the **AI detection**, which
needs each commit's untruncated message; what changes is that the file count no longer depends on
it.

#### Scenario: The count is the pull request's own diff
- **WHEN** a pull request whose latest iteration changes five files is ingested, and its commits together touch thousands of items
- **THEN** the event records five

#### Scenario: A merge commit inside the pull request does not weigh
- **WHEN** a pull request contains a merge commit carrying files from another branch
- **THEN** those files are not counted, because they are not in the pull request's diff

#### Scenario: A file touched in several commits counts once
- **WHEN** the same file is changed by several commits of the pull request
- **THEN** it counts once

#### Scenario: The latest iteration is the one read
- **WHEN** a pull request has several iterations
- **THEN** the diff of the one with the highest id is counted

#### Scenario: A paged diff is counted whole
- **WHEN** the diff arrives in more than one page
- **THEN** every page is read and the counts are added

#### Scenario: Folder entries are not files
- **WHEN** the diff contains entries describing folders
- **THEN** they are not counted

#### Scenario: A failed read leaves the pull request without a count
- **WHEN** the iterations or the diff of a pull request cannot be read
- **THEN** that pull request is ingested with no file count, the failure is logged with its id, and the sync continues
