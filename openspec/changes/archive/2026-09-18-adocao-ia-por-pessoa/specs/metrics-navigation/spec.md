## ADDED Requirements

### Requirement: Missing data is shown as missing, never as zero
The served UI SHALL present the absence of a value explicitly and SHALL NOT display a fabricated
number in its place. This applies whenever the API published no value for that metric and node. A
value the engine actually computed as zero SHALL still be displayed as zero, since that is a
measurement rather than an absence.

#### Scenario: A panel with no published value
- **WHEN** a panel's metric is not published for the current node
- **THEN** the panel states that there is no data, instead of showing zero

#### Scenario: A measured zero is still shown
- **WHEN** the engine computes zero for a metric in the period
- **THEN** the panel displays zero
