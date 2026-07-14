## ADDED Requirements

### Requirement: Access scope is derived from the structure
The system SHALL resolve a non-admin, non-exec account's access scope from the
position of its linked Person in the structure:
- manager of a Team → that team plus the individuals of that team (coaching);
- manager of a Vertical → that vertical with its teams aggregated;
- otherwise a member → their own individual data plus their team's aggregate.

#### Scenario: Team manager sees the team and its people
- **WHEN** an account whose Person manages a team requests that team
- **THEN** access is granted, including the individual panels of that team's people

#### Scenario: Vertical manager sees the vertical aggregated
- **WHEN** an account whose Person manages a vertical requests that vertical
- **THEN** access is granted to the vertical and its teams' aggregates

#### Scenario: Member sees own data and team aggregate
- **WHEN** a member account requests its own individual data or its team's aggregate
- **THEN** access is granted

### Requirement: Explicit admin and exec grants
The system SHALL treat the account role `admin` as authoritative for the
configuration area (accounts, ADO integration, AI convention) and full data
access, and `exec` as authoritative for read-only access across all verticals
(no configuration).

#### Scenario: Admin reaches configuration
- **WHEN** an `admin` account calls an admin/config endpoint
- **THEN** access is granted

#### Scenario: Exec reads any vertical but not config
- **WHEN** an `exec` account requests any vertical
- **THEN** read access is granted
- **AND** the same account is denied configuration endpoints

### Requirement: Deny access outside scope
The system SHALL respond 403 when an authenticated account requests a node outside
its resolved scope.

#### Scenario: Member cannot read another team
- **WHEN** a member account requests a team it does not belong to and does not manage
- **THEN** the system responds 403

### Requirement: Person comparison is coaching-only
The system SHALL expose an individual's data and any person-vs-person comparison
only to that person's own account and to the manager of that person's team. There
is never a public leaderboard nor cross-team comparison of people.

#### Scenario: Only the team manager sees a report's individual data
- **WHEN** the manager of a person's team requests that person's individual panel
- **THEN** access is granted

#### Scenario: A peer cannot see another person's individual data
- **WHEN** a member requests a teammate's individual panel
- **THEN** the system responds 403
