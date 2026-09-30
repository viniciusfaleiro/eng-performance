# user-accounts Specification

## Purpose
TBD - created by archiving change estrutura-cadastro. Update Purpose after archive.
## Requirements
### Requirement: Admin manages login accounts
The system SHALL let an admin create, edit and remove platform accounts, each with a
name, unique email, perfil (`exec`/`manager`/`contributor`/`admin`) and status
(`active`/`invited`/`disabled`), optionally linked to a Person. Login and RBAC
enforcement are out of scope (S2).

#### Scenario: Create an account
- **WHEN** an admin creates an account with a name, email, password and role
- **THEN** the account is listed with that role and an `active` status by default

#### Scenario: Reject a duplicate email
- **WHEN** an admin creates an account with an email that already exists
- **THEN** the system rejects the operation as a conflict

### Requirement: Passwords are stored hashed
The system SHALL store account passwords only as a one-way hash; the raw password
is never persisted nor returned by any endpoint.

#### Scenario: Password is hashed on create
- **WHEN** an account is created with a password
- **THEN** the stored value is a hash, not the raw password

#### Scenario: Admin resets a password
- **WHEN** an admin sets a new password for an account
- **THEN** the stored hash changes and the raw password is not persisted

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
