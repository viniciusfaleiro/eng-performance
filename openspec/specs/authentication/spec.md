# authentication Specification

## Purpose
Platform-native login (email/senha) over the accounts registered in `user-accounts`:
a real login flow, current-identity + scope exposure, logout, and self-service
password change. Created by archiving change contas-login-rbac.

## Requirements

### Requirement: Login with email and password
The system SHALL authenticate a user by email and password against the stored
BCrypt hash and, on success, establish a session that subsequent requests use.
The raw password is never logged nor returned.

#### Scenario: Successful login
- **WHEN** a user posts valid email and password to the login endpoint
- **THEN** the system returns a session/token and the authenticated user

#### Scenario: Invalid credentials
- **WHEN** a user posts a wrong password or an unknown email
- **THEN** the system rejects the login as unauthorized (401) without revealing which was wrong

#### Scenario: Disabled account cannot log in
- **WHEN** a user with a `disabled` account posts valid credentials
- **THEN** the system rejects the login

### Requirement: Current identity and scope
The system SHALL expose the authenticated user's identity together with the access
scope resolved for them (the nodes they may see).

#### Scenario: Read current user
- **WHEN** an authenticated request calls `/auth/me`
- **THEN** it returns the user, role and the resolved scope

#### Scenario: Unauthenticated access is rejected
- **WHEN** an unauthenticated request calls a protected endpoint
- **THEN** the system responds 401

### Requirement: Logout and self password change
The system SHALL let an authenticated user end their session and change their own
password by supplying the current password.

#### Scenario: Logout ends the session
- **WHEN** an authenticated user logs out
- **THEN** the session is no longer valid for subsequent requests

#### Scenario: Change own password
- **WHEN** a user supplies the correct current password and a new one
- **THEN** the stored hash is updated and the old password no longer authenticates

#### Scenario: Wrong current password is rejected
- **WHEN** a user supplies an incorrect current password
- **THEN** the change is rejected and the stored hash is unchanged
