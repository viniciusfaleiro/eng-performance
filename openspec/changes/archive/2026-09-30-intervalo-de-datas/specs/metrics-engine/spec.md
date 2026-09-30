## ADDED Requirements

### Requirement: Metrics can be computed over an arbitrary date range
The system SHALL compute any metric over a date range chosen freely, not only over a calendar bucket
of the selected frequency, applying the same aggregation, population and as-of-event attribution it
applies to a bucket. The system SHALL reject a range that ends before it starts, and a range that
has not begun.

#### Scenario: A range that spans parts of several months
- **WHEN** a metric is requested for a range starting mid-month and ending mid-month
- **THEN** it is computed over exactly that range, not over the months it touches

#### Scenario: A median over a range is recomputed, not averaged
- **WHEN** a metric aggregated by median is requested over a range covering several buckets
- **THEN** it is the median over the whole range's population, not a combination of per-bucket values

#### Scenario: An invalid range is refused
- **WHEN** a range ends before it starts, or has not begun
- **THEN** the request is refused rather than answered

### Requirement: A range is compared against the preceding range of equal length
The system SHALL report a range's evolution against the range immediately before it, of the same
duration. For a period that is exactly a calendar bucket, this SHALL be the previous bucket, so the
existing behaviour is unchanged.

#### Scenario: A 45-day range compares against the previous 45 days
- **WHEN** a metric is requested over a 45-day range
- **THEN** its evolution compares against the 45 days immediately before that range

#### Scenario: A calendar bucket still compares against the previous bucket
- **WHEN** a metric is requested for a whole month
- **THEN** its evolution compares against the previous month, as before

### Requirement: The elapsed-slice comparison applies only to a bucket in progress
The system SHALL compare only an elapsed slice when the period being computed is a calendar bucket
that contains the present. A freely chosen range SHALL be compared in full, even when it ends today,
since the range is what the user asked for rather than an incomplete bucket.

#### Scenario: A chosen range ending today is compared in full
- **WHEN** a range chosen by the user ends today
- **THEN** its evolution compares the whole range against the whole preceding range

#### Scenario: The current bucket still compares like-for-like
- **WHEN** the period is the calendar bucket containing today and is partially elapsed
- **THEN** its evolution compares the elapsed slice against the same slice of the previous bucket

### Requirement: A range's series is sliced by the selected frequency
When a metric series is requested over a range, the system SHALL produce one point per interval of
the selected frequency within that range, so the axis keeps calendar meaning. Rate metrics expressed
per day SHALL divide by the days of the range being computed, not by the days of a calendar bucket.

#### Scenario: A quarter at weekly frequency
- **WHEN** a series is requested over a three-month range at weekly frequency
- **THEN** it has one point per week within the range

#### Scenario: A per-day rate over a range
- **WHEN** a per-day rate metric is computed over a 45-day range
- **THEN** its value divides the range's total by 45
