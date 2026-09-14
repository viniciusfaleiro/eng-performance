## ADDED Requirements

### Requirement: Self-service password reset request
The system SHALL let an unauthenticated user request a password reset by supplying only their
email address, and SHALL send a message containing a single-use reset link to that address when it
belongs to an account that is not disabled. The raw reset token SHALL exist only in the message;
the stored form SHALL be a one-way hash of it.

#### Scenario: Reset requested for an existing account
- **WHEN** an unauthenticated user posts the email of an active account to the reset endpoint
- **THEN** the system issues a reset token, stores only its hash, and sends a message with a link carrying the raw token

#### Scenario: Stored token is not usable as-is
- **WHEN** a reset token has been issued
- **THEN** the persisted record contains a hash, never the token that was emailed

### Requirement: Reset requests do not reveal which emails exist
The system SHALL answer every reset request identically — same status, same body — whether the
email belongs to an active account, a disabled account, or no account at all, so the public
endpoint cannot be used to enumerate accounts. A disabled account SHALL be treated exactly like an
unknown email and SHALL NOT receive a message.

#### Scenario: Unknown email
- **WHEN** a reset is requested for an email that has no account
- **THEN** the system accepts the request with the same response as a successful one and sends no message

#### Scenario: Disabled account
- **WHEN** a reset is requested for a disabled account
- **THEN** the system responds identically to the unknown-email case and sends no message

### Requirement: Reset tokens expire and are single-use
The system SHALL accept a reset token only while it is unexpired and unconsumed. Consuming a token
SHALL mark it used in the same transaction that changes the password, so a token can never be
replayed and a failed password change never burns the token.

#### Scenario: Token is consumed on first successful use
- **WHEN** a user confirms a reset with a valid token and a new password
- **THEN** the password hash is updated and the token is marked consumed

#### Scenario: Replay is rejected
- **WHEN** a user confirms a reset with a token that was already consumed
- **THEN** the system rejects the request and the stored password hash is unchanged

#### Scenario: Expired token is rejected
- **WHEN** a user confirms a reset with a token whose validity window has passed
- **THEN** the system rejects the request and the stored password hash is unchanged

#### Scenario: Unknown token is rejected
- **WHEN** a user confirms a reset with a token that was never issued
- **THEN** the system rejects the request without revealing whether any token exists

### Requirement: A new request supersedes pending tokens
The system SHALL invalidate any still-pending reset token of an account when a new token is issued
for it, so only the most recently emailed link works.

#### Scenario: Older link stops working
- **WHEN** a second reset is requested for the same account and then the first token is submitted
- **THEN** the system rejects the first token and accepts only the most recent one

### Requirement: Reset issuance is rate limited per account
The system SHALL cap how many reset tokens an account can have issued within a time window.
Requests beyond the cap SHALL NOT send a message, SHALL NOT raise a distinguishable error, and
SHALL return the same response as any other reset request.

#### Scenario: Burst of requests for the same email
- **WHEN** more reset requests are made for one account within the window than the cap allows
- **THEN** only up to the cap produce messages and every request still returns the same response

### Requirement: Reset changes the password and invalidates the old one
The system SHALL replace the account's stored hash with a hash of the new password, SHALL reject a
blank new password, and SHALL never persist or return the raw password.

#### Scenario: Old password no longer authenticates
- **WHEN** a reset completes successfully
- **THEN** logging in with the previous password fails and logging in with the new password succeeds

#### Scenario: Blank new password is rejected
- **WHEN** a reset is confirmed with an empty new password
- **THEN** the system rejects the request and the stored hash is unchanged

### Requirement: Reset screen reached from the login screen
The served UI SHALL offer a "forgot my password" path from the login screen, and SHALL present a
"set a new password" screen when opened with a reset token in the link. After a successful reset
the UI SHALL remove the token from the browser URL and return the user to login.

#### Scenario: Requesting from the login screen
- **WHEN** a user follows the forgot-password link and submits their email
- **THEN** the UI confirms that a message was sent if the address is registered, without stating whether it exists

#### Scenario: Opening the emailed link
- **WHEN** the app is opened with a reset token in the URL
- **THEN** the new-password screen is shown instead of the login overlay

#### Scenario: Token is cleared after use
- **WHEN** the new password is confirmed successfully
- **THEN** the UI drops the token from the URL and shows the login screen
