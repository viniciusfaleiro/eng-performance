# email-delivery Specification

## Purpose
TBD - created by archiving change reset-senha-email. Update Purpose after archive.

## Requirements

### Requirement: Admin configures the outgoing email server
The system SHALL let an admin configure and persist the SMTP server used for transactional email —
host, port, transport security, username, password, sender address and display name — plus the
application base URL used to build links in messages. The configuration SHALL survive restarts and
take effect without redeploying.

#### Scenario: Save and read back the configuration
- **WHEN** an admin saves the email settings
- **THEN** reading them returns the saved host, port, security, username, sender and base URL

#### Scenario: Configuration applies without restart
- **WHEN** an admin saves valid SMTP settings and a message is then sent
- **THEN** the message is delivered through the newly saved server

### Requirement: The SMTP password is never exposed
The system SHALL treat the SMTP password as write-only: no endpoint SHALL return it, reading the
configuration SHALL only indicate whether a password is set, and saving the configuration without
supplying a password SHALL preserve the stored one. The password SHALL be encrypted at rest.

#### Scenario: Reading the configuration
- **WHEN** an admin reads the email settings
- **THEN** the response indicates whether a password is set but never contains the password

#### Scenario: Saving without a new password
- **WHEN** an admin saves the settings omitting the password field
- **THEN** the previously stored password is kept unchanged

#### Scenario: Stored form is encrypted
- **WHEN** a password is saved
- **THEN** the persisted value is ciphertext, not the password in clear text

### Requirement: Test message validates the configuration
The system SHALL let an admin send a test message to a chosen address and SHALL report the outcome,
surfacing the delivery error when the send fails.

#### Scenario: Successful test
- **WHEN** an admin sends a test message with valid settings
- **THEN** the system reports success and the message is delivered

#### Scenario: Failing test reports the error
- **WHEN** an admin sends a test message with an unreachable or rejecting server
- **THEN** the system reports the failure with the reason, and does not claim the message was sent

### Requirement: Log fallback when no email server is configured
When no SMTP server is configured and enabled, the system SHALL still accept send requests and
SHALL record the recipient, subject and body (including any link) in the application log instead of
delivering them, so the flows that depend on email remain exercisable without a mail server. The
admin UI SHALL make this fallback state visible.

#### Scenario: Sending with no configuration
- **WHEN** a message is sent while no SMTP server is configured
- **THEN** its recipient, subject and body are written to the application log and the flow completes

#### Scenario: Fallback state is visible
- **WHEN** an admin opens the email settings with no server configured
- **THEN** the UI states that email is running in log mode

#### Scenario: A configured server that fails does not fall back
- **WHEN** a configured SMTP server rejects a message
- **THEN** the system reports the failure instead of logging the message as if it had been sent
