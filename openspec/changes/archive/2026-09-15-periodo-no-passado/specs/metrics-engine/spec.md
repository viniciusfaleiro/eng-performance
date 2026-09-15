## ADDED Requirements

### Requirement: The reference period is a parameter, not always the present
The system SHALL accept the period to compute as an input, identified by the frequency and any date
inside it, and SHALL resolve that date to the bucket containing it. When no period is given, the
system SHALL compute the period containing the present. The system SHALL reject a period that has
not started yet, and SHALL NOT reject a past period merely because no events fall in it.

#### Scenario: Any date inside the period resolves to the period
- **WHEN** a monthly metric is requested for a date in the middle of a month
- **THEN** the value returned is that whole month's value

#### Scenario: No period given
- **WHEN** a metric is requested without a period
- **THEN** the period containing the present is computed

#### Scenario: A future period is refused
- **WHEN** a period that has not begun is requested
- **THEN** the request is refused, rather than answered with zeros

#### Scenario: An empty past period is answered
- **WHEN** a past period with no events is requested
- **THEN** the response reports zero for that period, with its coverage, rather than an error

## MODIFIED Requirements

### Requirement: Values are bucketed by frequency with correct-polarity evolution
The system SHALL bucket events by Diário, Semanal (ISO week, Monday start), or Mensal
in UTC, and SHALL report each metric's evolution versus the immediately previous
bucket using the metric's `direction`, so that a real improvement reads positive even
when the raw number decreased. When the bucket being computed is the one still in progress, the
comparison SHALL use the same elapsed slice of the previous bucket rather than the previous full
bucket; when the bucket being computed has already ended, the comparison SHALL use the previous
bucket in full.

#### Scenario: Weekly value compares against the prior week
- **WHEN** a metric series is requested at weekly frequency
- **THEN** each bucket reports its value and its evolution versus the previous week

#### Scenario: A lower-is-better metric that falls reads as an improvement
- **WHEN** a metric whose direction is "lower is better" decreases versus the previous period
- **THEN** its evolution is reported as a positive/good change

#### Scenario: Current partial period compares like-for-like
- **WHEN** the bucket being computed is the one in progress and is only partially elapsed
- **THEN** its evolution compares the elapsed slice to the same elapsed slice of the previous bucket

#### Scenario: A completed past period compares in full
- **WHEN** a past period that has already ended is computed
- **THEN** its evolution compares the full period against the full previous period, with no elapsed
  slice applied
