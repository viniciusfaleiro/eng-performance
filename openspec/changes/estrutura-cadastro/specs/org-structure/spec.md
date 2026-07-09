## ADDED Requirements

### Requirement: Fixed three-level hierarchy
The system SHALL model the organization as exactly three levels — Vertical →
Team → Person — and MUST reject any attempt to nest levels beyond this depth.

#### Scenario: Register a full hierarchy
- **WHEN** an admin creates a vertical, a team under that vertical, and a person on that team
- **THEN** the person resolves to that team and the team resolves to that vertical

#### Scenario: Reject a team without a vertical
- **WHEN** an admin creates a team referencing a non-existent vertical
- **THEN** the system rejects the operation with a validation error

### Requirement: Dated team membership (as-of-event)
A Person SHALL belong to exactly one team at any given time through a
`TeamMembership` with a start date and an optional end date. The current team is
the membership without an end date.

#### Scenario: Person has a single current team
- **WHEN** a person is created on a team with an effective date
- **THEN** a membership with that start date and no end date exists and is the current team

#### Scenario: Reject overlapping open memberships
- **WHEN** a person already has an open membership and another open membership is created
- **THEN** the system rejects the operation

### Requirement: Moving a person preserves history
When a person changes teams, the system SHALL close the current membership on the
day before the effective date and open a new membership from the effective date,
so past periods remain attributable to the team of record.

#### Scenario: Team change keeps the prior membership
- **WHEN** an admin moves a person to a new team effective on a date
- **THEN** the previous membership is closed with an end date before that date
- **AND** a new open membership on the new team starts on the effective date

### Requirement: Manual managers for team and vertical
The system SHALL let an admin set, for each Team and each Vertical, a manager
that is a registered Person. Managers are not derived from the ADO.

#### Scenario: Assign a team manager
- **WHEN** an admin sets a person as the manager of a team
- **THEN** the team reports that person as its manager

### Requirement: Structure tree navigation
The system SHALL expose the organization as a navigable tree (Vertical → Team →
Person) rooted at an "overview" node.

#### Scenario: Tree reflects the registered structure
- **WHEN** the structure tree is requested
- **THEN** it returns the overview root containing every vertical, its teams, and their current people
