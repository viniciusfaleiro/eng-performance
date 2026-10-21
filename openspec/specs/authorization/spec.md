# authorization Specification

## Purpose
RBAC derived from a user's Person position in the org structure, plus the explicit
`admin`/`exec` grants. Enforces scoped visibility (403 outside scope) and the
coaching-only rule for individual data. Created by archiving change contas-login-rbac.
## Requirements
### Requirement: Access scope is derived from the structure
The system SHALL resolve a non-admin, non-exec account's access scope from the
position of its linked Person in the structure:
- manager of a Team → that team plus the individuals of that team (coaching);
- manager of a Vertical → that vertical with its teams aggregated;
- otherwise a member → their own individual data plus **every aggregate above them**: their
  team, the vertical that team belongs to, and the organisation.

A member SHALL see the whole chain above their own position because that chain is what gives their
own number meaning: a cycle time of 40 h says nothing alone and says everything beside a team at 38
and an organisation at 20. What the scope SHALL NOT include is anything **beside** them — another
team, another vertical, or another person — which is the comparison the product refuses, and which
no amount of context justifies.

#### Scenario: Team manager sees the team and its people
- **WHEN** an account whose Person manages a team requests that team
- **THEN** access is granted, including the individual panels of that team's people

#### Scenario: Vertical manager sees the vertical aggregated
- **WHEN** an account whose Person manages a vertical requests that vertical
- **THEN** access is granted to the vertical and its teams' aggregates

#### Scenario: Member sees own data and every aggregate above
- **WHEN** a member account requests its own individual data, its team's aggregate, its vertical's aggregate or the organisation's
- **THEN** access is granted in all four cases

#### Scenario: Member cannot step sideways
- **WHEN** a member account requests a sibling team, another vertical or a teammate's individual panel
- **THEN** access is denied

### Requirement: Scope is independent of commit activity
The system SHALL derive access scope only from the account's role and its linked
Person's position in the structure (`Team.managerId`, `Vertical.managerId`, team
membership) and SHALL NOT require the Person to have any committer identity or
commit history. The Person↔account link (login/RBAC) and the Person↔committer-
identity link (metric attribution) are independent: a manager with no commits is a
fully supported case — they authenticate and receive the correct scope, while their
own individual metrics are simply empty and team/vertical aggregates come from the
committing members.

#### Scenario: A manager with no commits still gets the correct scope
- **WHEN** an account is linked to a Person who manages a team or vertical but has
  no committer identity (zero commits attributed)
- **THEN** login succeeds and the account's scope covers that team/vertical exactly
  as it would for a manager who commits

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
its resolved scope. For a member the line is **lateral, not vertical**: everything above them is in
scope and everything beside them is out, however close it sits in the structure.

Where a view compares the children of a node — the comparison matrix, the scatter — the rows SHALL
be filtered by the same scope, so reaching an aggregate never becomes a way around the lateral
rule: a member who opens the organisation sees the organisation's figures, and among the compared
rows only the ones they could already open.

#### Scenario: Member cannot read another team
- **WHEN** a member account requests a team it does not belong to and does not manage
- **THEN** the system responds 403

#### Scenario: Reaching an aggregate does not expose its other children
- **WHEN** a member opens the organisation or their vertical, where children are compared side by side
- **THEN** only the children already in their scope are listed, and the siblings are absent

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

