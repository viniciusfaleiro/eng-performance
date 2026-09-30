## ADDED Requirements

### Requirement: The period control offers a custom date range
The served UI SHALL let the user choose an arbitrary date range from the same control that selects a
day, week or month, and SHALL apply it to every surface the selected period already applies to. The
control SHALL present the chosen range's dates, and SHALL offer a way back to a calendar period.

#### Scenario: Choosing a range
- **WHEN** the user picks a start and end date
- **THEN** every displayed metric is recomputed over that range

#### Scenario: The range is visible
- **WHEN** a custom range is active
- **THEN** the control shows the range's start and end, rather than a calendar period

#### Scenario: Returning to a calendar period
- **WHEN** the user leaves the custom range
- **THEN** the view returns to a calendar period and its stepping controls

### Requirement: Weekly and monthly periods can be read as a calendar period or a rolling window
The served UI SHALL let the user switch a weekly or monthly period between the calendar reading —
the ISO week or the calendar month — and a rolling window of the last seven or thirty days ending at
the period in view. Stepping SHALL move one calendar period in the calendar reading and one window
length in the rolling reading. The daily frequency SHALL NOT offer the choice, since a one-day
window and the day itself are the same interval.

#### Scenario: Switching the month to the last thirty days
- **WHEN** the user switches a monthly period to the rolling reading
- **THEN** the metrics are recomputed over the last thirty days instead of the calendar month

#### Scenario: The calendar reading of the current period is still partial
- **WHEN** the calendar reading is active and the period is the one containing today
- **THEN** it covers from the period's first day up to today, as before

#### Scenario: A rolling window is always its full length
- **WHEN** the rolling reading is active
- **THEN** the window covers its full length and is compared against the preceding window of the
  same length, with no elapsed slice applied

#### Scenario: Stepping follows the reading
- **WHEN** the user steps back with the rolling reading active
- **THEN** the window moves by its own length rather than to the previous calendar period

#### Scenario: Daily offers no choice
- **WHEN** the frequency is daily
- **THEN** no calendar/rolling choice is offered

#### Scenario: The active reading is visible
- **WHEN** either reading is active
- **THEN** the period control names it, so a screenshot without the control is not ambiguous

### Requirement: With a range active, frequency is the chart's granularity
When a custom range is active, the frequency SHALL determine how the range is sliced for the
evolution chart rather than which calendar period is displayed, and the UI SHALL say so, since the
same control means two different things in the two modes.

#### Scenario: Changing frequency with a range active
- **WHEN** the user changes frequency while a custom range is active
- **THEN** the range stays the same and the chart's points change granularity

#### Scenario: The meaning is stated
- **WHEN** a custom range is active
- **THEN** the UI indicates that the frequency is the chart's granularity

### Requirement: The comparison baseline is stated when it is not a calendar period
The served UI SHALL make the comparison baseline explicit whenever the evolution is computed against
a preceding range rather than a previous calendar period, so a percentage is not read against the
wrong window.

#### Scenario: Evolution over a custom range
- **WHEN** a custom range is active and an evolution is displayed
- **THEN** the UI identifies the range it is being compared against

### Requirement: A custom range is shareable as a link
The system SHALL carry a chosen range in the address, so reopening the address shows the same range.

#### Scenario: Reopening an address with a range
- **WHEN** an address captured while a custom range was active is opened later
- **THEN** it shows the same range
