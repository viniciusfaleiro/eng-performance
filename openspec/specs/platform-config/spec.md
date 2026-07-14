# platform-config Specification

## Purpose
TBD - created by archiving change estrutura-cadastro. Update Purpose after archive.
## Requirements
### Requirement: Azure DevOps connection is configured and persisted
The system SHALL persist the Azure DevOps connection (organization URL, PAT,
production-stage rule). The PAT MUST never be returned in clear — reads return a
redacted marker. Actually validating/syncing against ADO is out of scope (S9).

#### Scenario: Save and read back the connection
- **WHEN** an admin saves the organization URL, a PAT and the production-stage rule
- **THEN** reading the config returns the URL and rule and a redacted PAT (never the secret)

#### Scenario: Saving with a blank PAT keeps the stored secret
- **WHEN** an admin saves the config leaving the PAT field blank
- **THEN** the previously stored secret is preserved

#### Scenario: Test connection marks it connected
- **WHEN** an admin triggers "test connection"
- **THEN** the config is marked connected with a validation timestamp

### Requirement: AI detection convention is configured and persisted
The system SHALL persist the convention that marks a commit as AI-assisted
(strategy trailer/tag plus a detection regex).

#### Scenario: Save and read back the convention
- **WHEN** an admin saves a strategy and a detection regex
- **THEN** reading the convention returns the saved strategy and regex

