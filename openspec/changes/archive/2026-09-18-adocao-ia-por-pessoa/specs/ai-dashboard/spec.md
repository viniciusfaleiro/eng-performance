## MODIFIED Requirements

### Requirement: The IA comparison compares structures only
The system SHALL provide an AI-adoption ranking of the node's children — at the overview
the verticals, within a vertical its teams, and within a team the people of that team — and the
with/without-AI panels over the node's population. The system SHALL NOT produce a ranking at the
person level, and SHALL NOT compare people from different teams. A person SHALL appear in a ranking
only to a caller allowed to view that person individually, so the comparison stays the manager's
coaching over their own team rather than a public league table.

#### Scenario: Overview ranking compares verticals
- **WHEN** the adoption ranking is requested at the overview node
- **THEN** each vertical appears with its adoption, and no people appear

#### Scenario: A team ranks its own people
- **WHEN** the ranking is requested for a team by a caller who may view those people individually
- **THEN** the people of that team appear with their adoption

#### Scenario: People outside the caller's individual scope are withheld
- **WHEN** the ranking is requested for a team by a caller who may see the team but not its people
  individually
- **THEN** those people are not included in the ranking

#### Scenario: A person produces no ranking
- **WHEN** the ranking is requested for a person
- **THEN** no ranking is produced, since that would compare them with their peers
