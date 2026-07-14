# committer-identity Specification

## Purpose
TBD - created by archiving change estrutura-cadastro. Update Purpose after archive.
## Requirements
### Requirement: Discover committer identities
The system SHALL surface the committer identities (commit emails / PR users)
present in the ingested data, each with its display name and a mapped/unmapped
status. In this slice the identities come from fixtures.

#### Scenario: List discovered identities
- **WHEN** an admin opens the identities view
- **THEN** every discovered identity is listed with its mapped status and, when mapped, the linked person

### Requirement: Link identity to a person
The system SHALL allow an admin to link a committer identity to a registered
Person, and to unlink it. A person MAY have several identities.

#### Scenario: Map an identity
- **WHEN** an admin links an unmapped identity to a person
- **THEN** the identity becomes mapped to that person

#### Scenario: Unmap an identity
- **WHEN** an admin unlinks a mapped identity
- **THEN** the identity returns to the unmapped state

### Requirement: Unattributed bucket and coverage indicator
Identities that are not linked to a Person SHALL be kept in a "Não atribuído"
bucket, and the system SHALL report an attribution coverage percentage
(attributed events over total).

#### Scenario: Coverage reflects mapping changes
- **WHEN** an admin maps a previously unmapped identity that carries events
- **THEN** the reported coverage percentage increases

