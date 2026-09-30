## ADDED Requirements

### Requirement: The individual panel reports platform access apart from delivery
The individual panel SHALL report when that person last signed in to the platform, presented as
platform access and visually separated from the delivery figures, so it is not read as part of the
person's engineering performance. It SHALL NOT be ranked, compared between people, or charted over
time. When the person has no account, the panel SHALL omit it rather than assert anything.

#### Scenario: A person with an account
- **WHEN** the individual panel is shown for a person whose account has signed in
- **THEN** the panel reports when that person last signed in, outside the delivery figures

#### Scenario: A person who never signed in
- **WHEN** the person's account has never signed in
- **THEN** the panel says so explicitly

#### Scenario: A person with no account
- **WHEN** the person has no account on the platform
- **THEN** the panel omits platform access entirely
