## ADDED Requirements

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
