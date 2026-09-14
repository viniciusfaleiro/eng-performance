## MODIFIED Requirements

### Requirement: Delivery trends reuse the person-scoped metrics
The system SHALL include the person's delivery trends — throughput, cycle time, % of commits
with AI, and the period's volume of **commits** and **pull requests** — as the same engine series
computed for that person node, with correct-polarity evolution. The volume series SHALL come from
the same `commit_count` and `pr_count` metrics served on the Fluxo dashboard, so the individual
panel and the team view never disagree on the same person's numbers, and SHALL remain
coaching-only: they are shown inside the individual panel and never aggregated into any public
ranking or comparison.

#### Scenario: Delivery series returned for the person
- **WHEN** the individual panel is requested for a person and frequency
- **THEN** throughput, cycle time, %-with-AI, commits and pull-requests series for that person are returned

#### Scenario: Volume series stay coaching-only
- **WHEN** a person's commit and pull-request volume is computed for the individual panel
- **THEN** it is served only to an admin or the managing/own account and is never added to a cross-structure ranking or comparison
