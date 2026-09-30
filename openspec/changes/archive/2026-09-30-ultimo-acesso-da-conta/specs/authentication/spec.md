## ADDED Requirements

### Requirement: A successful sign-in records when it happened
The system SHALL record the moment of each successful authentication on the account that signed in,
replacing the previously recorded moment. A failed sign-in SHALL NOT record anything. Failing to
record SHALL NOT prevent the sign-in from completing, since losing someone's access to keep an
adoption statistic would invert the priority.

#### Scenario: Signing in updates the account's last access
- **WHEN** an account signs in successfully
- **THEN** that account's last access becomes the moment of the sign-in

#### Scenario: A rejected sign-in leaves no trace
- **WHEN** a sign-in is rejected for wrong credentials or a disabled account
- **THEN** the account's last access is unchanged

#### Scenario: Recording is not load-bearing
- **WHEN** recording the access fails
- **THEN** the sign-in still completes
