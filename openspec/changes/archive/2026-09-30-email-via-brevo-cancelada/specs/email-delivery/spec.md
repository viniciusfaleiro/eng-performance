## ADDED Requirements

### Requirement: Transactional email is delivered over HTTPS
The system SHALL be able to deliver transactional email through an HTTPS email API (Brevo),
without relying on outbound SMTP. The send SHALL use the configured API key and the configured
sender address, and SHALL report the provider's own error detail when the provider rejects the
message.

#### Scenario: Message accepted by the provider
- **WHEN** the HTTPS provider is selected with a valid API key and a message is sent
- **THEN** the message is submitted to the provider and the send reports success

#### Scenario: Provider rejects the message
- **WHEN** the provider answers with an error (for example an invalid key or an unverified sender)
- **THEN** the send fails reporting the provider's error detail, and does not claim success

#### Scenario: The API key never appears in an error
- **WHEN** a send fails and the failure is reported
- **THEN** the reported message carries the provider's response but never the API key

### Requirement: The email provider is chosen explicitly
The system SHALL let an admin choose which provider delivers transactional email — log, SMTP, or
the HTTPS API — and SHALL deliver through exactly the chosen one. Choosing the log provider SHALL
be a valid configuration, not an error state. The system SHALL NOT infer the provider from which
fields happen to be filled in.

#### Scenario: Switching providers takes effect
- **WHEN** an admin changes the provider and saves
- **THEN** the next message is delivered through the newly chosen provider

#### Scenario: Unused provider fields are not required
- **WHEN** an admin saves with the HTTPS provider selected and no SMTP host
- **THEN** the configuration is accepted

#### Scenario: Log is a deliberate choice
- **WHEN** an admin selects the log provider and saves
- **THEN** messages are written to the application log and the UI presents this as the configured
  state, not as a missing configuration

### Requirement: The email API key is never exposed
The system SHALL treat the HTTPS provider's API key as write-only: no endpoint SHALL return it,
reading the configuration SHALL only indicate whether a key is set, and saving the configuration
without supplying a key SHALL preserve the stored one. The key SHALL be encrypted at rest.

#### Scenario: Reading the configuration
- **WHEN** an admin reads the email settings
- **THEN** the response indicates whether an API key is set but never contains the key

#### Scenario: Saving without a new key
- **WHEN** an admin saves the settings omitting the API key field
- **THEN** the previously stored key is kept unchanged

#### Scenario: Stored form is encrypted
- **WHEN** an API key is saved
- **THEN** the persisted value is ciphertext, not the key in clear text

## MODIFIED Requirements

### Requirement: Admin configures the outgoing email server
The system SHALL let an admin configure and persist how transactional email is delivered — the
chosen provider, the SMTP server settings it needs when SMTP is chosen (host, port, transport
security, username, password), the API key when the HTTPS provider is chosen, and the sender
address and display name — plus the application base URL used to build links in messages. Sender
address, display name and base URL SHALL be shared by every provider, since they describe the
message and not the transport. The configuration SHALL survive restarts and take effect without
redeploying.

#### Scenario: Save and read back the configuration
- **WHEN** an admin saves the email settings
- **THEN** reading them returns the saved provider, host, port, security, username, sender and base
  URL

#### Scenario: Configuration applies without restart
- **WHEN** an admin saves valid settings and a message is then sent
- **THEN** the message is delivered through the newly saved configuration

#### Scenario: The sender is shared across providers
- **WHEN** an admin changes the provider without changing the sender address
- **THEN** messages sent afterwards keep the same sender address and display name

### Requirement: Test message validates the configuration
The system SHALL let an admin send a test message to a chosen address using the currently
configured provider, and SHALL report the outcome, surfacing the delivery error when the send
fails.

#### Scenario: Successful test
- **WHEN** an admin sends a test message with valid settings
- **THEN** the system reports success and the message is delivered through the configured provider

#### Scenario: Failing test reports the error
- **WHEN** an admin sends a test message and the configured provider is unreachable or rejects it
- **THEN** the system reports the failure with the reason, and does not claim the message was sent

### Requirement: Log fallback when no email server is configured
When the log provider is configured, the system SHALL accept send requests and SHALL record the
recipient, subject and body (including any link) in the application log instead of delivering them,
so the flows that depend on email remain exercisable without a mail server. The admin UI SHALL make
this state visible. A provider that is configured but fails SHALL NOT fall back to the log.

#### Scenario: Sending in log mode
- **WHEN** a message is sent while the log provider is configured
- **THEN** its recipient, subject and body are written to the application log and the flow completes

#### Scenario: Log state is visible
- **WHEN** an admin opens the email settings with the log provider configured
- **THEN** the UI states that email is running in log mode

#### Scenario: A configured provider that fails does not fall back
- **WHEN** a configured SMTP server or HTTPS provider rejects a message
- **THEN** the system reports the failure instead of logging the message as if it had been sent
