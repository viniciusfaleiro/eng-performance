## MODIFIED Requirements

### Requirement: PR assertiveness is the first-pass approval rate
The system SHALL report PR assertiveness as the share of the person's PRs that were approved without
any reviewer ever asking for changes, over all their PRs, person-scoped and higher-is-better. A PR
SHALL NOT count as first-pass when any reviewer cast a negative vote at any point in the pull
request's history, even if that reviewer later approved it. A PR whose vote history cannot be read
SHALL NOT count as first-pass.

#### Scenario: Assertiveness computed
- **WHEN** the individual panel is requested for a person with some PRs approved first-pass
- **THEN** the assertiveness rate equals first-pass approvals over all their PRs

#### Scenario: A rejection followed by an approval is not first-pass
- **WHEN** a reviewer rejected a pull request and approved it after the author made changes
- **THEN** that pull request does not count as a first-pass approval

#### Scenario: Unknown history is not counted as success
- **WHEN** a pull request's vote history cannot be read
- **THEN** it does not count as a first-pass approval

### Requirement: Code-review contribution reports both directions
The system SHALL report the person's code-review contribution: comments made, approvals given
and rejections given (from reviews where the person is the reviewer), and reviews given vs
reviews received (where the person is the reviewed PR's author). A reviewer who asked for changes at
any point SHALL be reported as having given a change request for that pull request, even if they
approved it afterwards.

#### Scenario: Given and received are distinct
- **WHEN** the person reviewed others' PRs and also received reviews on their own PRs
- **THEN** reviews given count the person as reviewer and reviews received count the person as author

#### Scenario: Approvals and rejections split by decision
- **WHEN** the person's reviews include approvals and change-requests
- **THEN** approvals given and rejections given are reported separately

#### Scenario: A change request is not erased by a later approval
- **WHEN** a reviewer asked for changes on a pull request and later approved it
- **THEN** that review counts as a change request given
