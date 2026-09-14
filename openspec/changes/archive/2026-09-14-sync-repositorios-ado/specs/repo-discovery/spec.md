## ADDED Requirements

### Requirement: Discovery lists a project's real repositories and diffs the registry
Given an organization and a project, the system SHALL authenticate interactively (the same
device-code flow used by the historical sync, no PAT), list that project's Git repositories from
Azure DevOps, and compare them against the repositories already registered for that same
organization/project — case-insensitively by name.

#### Scenario: Discovery reports repositories to insert
- **WHEN** discovery runs for an organization/project and Azure DevOps has a repository not present in the registry for that organization/project
- **THEN** the diff lists that repository as one to insert

#### Scenario: Discovery reports repositories to remove
- **WHEN** discovery runs for an organization/project and the registry has a repository no longer present in Azure DevOps for that organization/project
- **THEN** the diff lists that repository as one to remove

#### Scenario: Repositories outside the given organization/project are untouched
- **WHEN** discovery runs for one organization/project
- **THEN** repositories registered under a different organization or project never appear in the diff

### Requirement: The diff is applied only on explicit confirmation
The system SHALL NOT insert or remove any repository as a side effect of computing the diff — an
admin SHALL confirm before the diff is applied to the registry.

#### Scenario: Viewing the diff changes nothing
- **WHEN** a discovery run finishes and its diff is available
- **THEN** the repository registry is unchanged until the admin applies the diff

#### Scenario: Applying inserts and removes exactly the diffed repositories
- **WHEN** an admin applies a diff with N repositories to insert and M to remove
- **THEN** exactly those N repositories are registered (unmapped) and exactly those M are removed, and nothing else changes

### Requirement: A newly inserted repository attempts automatic production-stage detection
For each repository the diff inserts, the system SHALL attempt to detect its production-stage rule
from that repository's recent pipeline runs, matching stage names against the same production-name
heuristic already used to classify deploys. When exactly one distinct matching stage name is found
across those runs, it SHALL be set as the repository's production stage; when none or more than one
distinct candidate is found, the field SHALL be left empty rather than guessed.

#### Scenario: Exactly one candidate stage is set automatically
- **WHEN** a newly discovered repository's recent pipeline runs all agree on one stage name matching the production heuristic
- **THEN** that stage name is set as the repository's production stage on insertion

#### Scenario: No candidate leaves the field empty
- **WHEN** a newly discovered repository's recent pipeline runs have no stage name matching the production heuristic
- **THEN** the repository is inserted with no production stage set

#### Scenario: Ambiguous candidates leave the field empty
- **WHEN** a newly discovered repository's recent pipeline runs match more than one distinct stage name against the production heuristic
- **THEN** the repository is inserted with no production stage set, rather than picking one of them
