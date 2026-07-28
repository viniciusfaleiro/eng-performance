## MODIFIED Requirements

### Requirement: One repository maps to one team
The system SHALL let an admin register repositories **one by one** — each with its Azure DevOps
**organization**, project, repository key, and a **production-stage rule** — and map each to at most
one Team, replacing any prior mapping so a repository always belongs to exactly one team. The admin
MAY edit or delete a registered repository. There is **no org-wide discovery**. This registration is
the basis for attributing repository- and pipeline-scoped metrics (DORA) to a team.

#### Scenario: Register a repository and map it to a team
- **WHEN** an admin registers a repository (organization, project, key) and assigns it to a team
- **THEN** the repository is stored with its organization and reports that team as its owner

#### Scenario: A repository belongs to exactly one team
- **WHEN** an admin re-maps a repository that already belongs to a team
- **THEN** the previous mapping is replaced and the repository still belongs to exactly one team

#### Scenario: A repository carries its own production-stage rule
- **WHEN** an admin sets a repository's production stage
- **THEN** that rule is stored on the repository and is used to classify its production deploys
