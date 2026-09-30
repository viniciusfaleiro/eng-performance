## ADDED Requirements

### Requirement: Accounts report when they were last used
The system SHALL expose, for each account, when it last signed in, and SHALL distinguish an account
that has never signed in from one that signed in long ago. The administration listing SHALL present
this, so whoever runs the rollout can tell who is using the platform without opening each person's
panel.

#### Scenario: An account that has signed in
- **WHEN** an administrator lists the accounts
- **THEN** each account that has signed in shows when it last did

#### Scenario: An account that never signed in
- **WHEN** an account has never signed in
- **THEN** it is reported as never having signed in, rather than as missing or blank data

#### Scenario: Only administrators see the listing
- **WHEN** a caller without administration access requests the account listing
- **THEN** the request is refused, as before
