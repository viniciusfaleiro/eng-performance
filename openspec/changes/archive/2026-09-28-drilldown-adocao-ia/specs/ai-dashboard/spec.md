## ADDED Requirements

### Requirement: AI adoption can be broken down by person
The system SHALL let a user open the AI-adoption figure and see the people behind it, ranked by how
much of their work was AI-assisted — both those who used it most and those who used it least. The
breakdown SHALL cover the population of the node currently being viewed: the whole organisation at
the overview, a vertical's people within a vertical, a team's people within a team.

#### Scenario: Breaking down adoption at a team
- **WHEN** a user opens the adoption breakdown while viewing a team
- **THEN** the people of that team are listed, ranked by their share of AI-assisted work

#### Scenario: Breaking down adoption at the overview
- **WHEN** a user opens the adoption breakdown at the overview
- **THEN** the listing covers the people of the whole organisation, within the caller's scope

#### Scenario: Both ends are reported
- **WHEN** the adoption breakdown is returned
- **THEN** it identifies both the people with the highest and the people with the lowest AI-assisted
  share

### Requirement: The adoption breakdown counts only people who produced code
The breakdown SHALL list only people with at least one commit in the period — the same population
the adoption ratio divides by. A person with no commits in the period SHALL NOT be reported as a
non-user of AI, since not producing code in a period is not evidence about AI at all.

#### Scenario: Someone who did not commit is absent
- **WHEN** a person made no commits in the period
- **THEN** they appear in neither end of the breakdown

#### Scenario: Someone who committed without AI is a non-user
- **WHEN** a person committed in the period and none of their commits is AI-assisted
- **THEN** they appear among the people with the lowest AI-assisted share

### Requirement: The adoption breakdown shows what each person contributed
Each person in the breakdown SHALL be reported with the numbers behind their position — their
AI-assisted commits and their total commits in the period — so the reader can tell a low share on
little work from a low share on much work.

#### Scenario: Counts accompany each person
- **WHEN** the breakdown lists a person
- **THEN** it reports their AI-assisted commit count and their total commit count for the period

### Requirement: The adoption breakdown respects individual access
A person SHALL appear in the breakdown only to a caller allowed to view that person individually.
The aggregate figure SHALL remain unchanged by this filtering, so the number and the list may cover
different populations.

#### Scenario: A manager sees only their own people
- **WHEN** a manager opens the adoption breakdown at a node covering people they may not view
  individually
- **THEN** only the people they may view individually are listed

#### Scenario: Filtering does not alter the figure
- **WHEN** the listing is filtered by access
- **THEN** the adoption percentage still reflects the whole population of the node
