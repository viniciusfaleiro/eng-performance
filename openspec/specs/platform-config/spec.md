# platform-config Specification

## Purpose
TBD - created by archiving change estrutura-cadastro. Update Purpose after archive.
## Requirements
### Requirement: AI detection convention is configured and persisted
The system SHALL persist the convention that marks a commit as AI-assisted
(strategy trailer/tag plus a detection regex).

#### Scenario: Save and read back the convention
- **WHEN** an admin saves a strategy and a detection regex
- **THEN** reading the convention returns the saved strategy and regex

