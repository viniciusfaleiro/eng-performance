## ADDED Requirements

### Requirement: Impediment convention is configured and persisted
The system SHALL persist the convention that marks a work item as impeded: the **reference name of
the field** that signals impediment and the **prefix of the tag** that signals it along with its
reason. Both SHALL be editable by an administrator and SHALL survive a restart.

A **default** convention SHALL be seeded on first boot, so a team that uses the common spelling gets
the behaviour without configuring anything, and seeding SHALL be idempotent. Leaving both values
empty SHALL be a valid configuration meaning "no impediment signal", in which case no flow figure
changes.

#### Scenario: Save and read back the convention
- **WHEN** an admin saves a field reference name and a tag prefix
- **THEN** reading the convention returns the saved field and prefix

#### Scenario: A default exists before anyone configures it
- **WHEN** the platform boots for the first time
- **THEN** the impediment convention already holds a default field name and tag prefix

#### Scenario: An empty convention disables the signal
- **WHEN** an admin clears both the field name and the tag prefix
- **THEN** no work item is reported as impeded and the flow figures match what they were without the signal
