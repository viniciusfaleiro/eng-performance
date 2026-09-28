## ADDED Requirements

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
