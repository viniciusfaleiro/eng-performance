## MODIFIED Requirements

### Requirement: Current identity and scope
The system SHALL expose the authenticated user's identity together with the access
scope resolved for them (the nodes they may see). Every route SHALL require a valid session
except a closed allowlist of public authentication routes: login, the password-reset request and
the password-reset confirmation.

#### Scenario: Read current user
- **WHEN** an authenticated request calls `/auth/me`
- **THEN** it returns the user, role and the resolved scope

#### Scenario: Unauthenticated access is rejected
- **WHEN** an unauthenticated request calls a protected endpoint
- **THEN** the system responds 401

#### Scenario: Password-reset routes are public
- **WHEN** an unauthenticated request calls the password-reset request or confirmation endpoint
- **THEN** the system processes it instead of responding 401

### Requirement: Logout and self password change
The system SHALL let an authenticated user end their session and change their own
password by supplying the current password. A password changed by either route — the authenticated
change or a completed reset — SHALL immediately stop authenticating the previous password.

#### Scenario: Logout ends the session
- **WHEN** an authenticated user logs out
- **THEN** the session is no longer valid for subsequent requests

#### Scenario: Change own password
- **WHEN** a user supplies the correct current password and a new one
- **THEN** the stored hash is updated and the old password no longer authenticates

#### Scenario: Wrong current password is rejected
- **WHEN** a user supplies an incorrect current password
- **THEN** the change is rejected and the stored hash is unchanged

#### Scenario: Password replaced by a reset stops authenticating
- **WHEN** a user completes a password reset and then logs in with the password it replaced
- **THEN** the login is rejected as unauthorized
