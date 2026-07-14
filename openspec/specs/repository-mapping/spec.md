# repository-mapping Specification

## Purpose
TBD - created by archiving change estrutura-cadastro. Update Purpose after archive.
## Requirements
### Requirement: One repository maps to one team
The system SHALL let an admin map each repository/project to at most one Team,
and MUST reject mapping a repository to a second team. This mapping is the basis
for attributing repository/pipeline-scoped metrics (DORA) to a team.

#### Scenario: Map a repository to a team
- **WHEN** an admin assigns a repository to a team
- **THEN** the repository reports that team as its owner

#### Scenario: Reject a second team for the same repository
- **WHEN** an admin assigns a repository that already belongs to a team to a different team
- **THEN** the previous mapping is replaced and the repository still belongs to exactly one team

### Requirement: Unmapped repositories are out of DORA scope
A repository not mapped to any team SHALL be flagged as unattributed and
excluded from team-level DORA scope.

#### Scenario: Unmapped repository is flagged
- **WHEN** the repositories view is shown and a repository has no team
- **THEN** that repository is marked as out of DORA scope

