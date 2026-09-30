## ADDED Requirements

### Requirement: An event occupies time either at an instant or over an interval
The metric catalog SHALL declare, per metric, whether its events occupy time **at an instant** or
**over an interval**, and the engine SHALL decide period membership accordingly:

- **Instant** — the event belongs to the period containing its own date. This is how a commit, a
  deploy, a closed pull request and a completed work item are counted, and it stays the default.
- **Interval** — the event belongs to every period its in-progress interval overlaps, and its own
  date is irrelevant. An item worked from 5 to 12 June belongs to June, to the week of the 8th and
  to each of those days, whatever date its record carries.

Membership SHALL be decided in **one place**, so that the value, the trend chart and the openable
item list can never disagree about which items a period contains.

An item's in-progress interval SHALL be read from the item's own state history. An item that has
**not** reached a terminal state SHALL have its interval extended to the **clock of the reading**,
not the clock of the ingestion that recorded it — otherwise a daily reading reports what the
scheduler last saw instead of what is open, and the number silently depends on when the sync ran.

The reading clock is the same injected clock the rest of the engine uses, so a pinned demo
environment stays deterministic.

#### Scenario: An interval metric counts an item the period overlaps
- **WHEN** a work item was in progress from 5 to 12 June and the selected period is June
- **THEN** it is counted, regardless of the date on its event record

#### Scenario: An interval metric counts an item still open and untouched
- **WHEN** a work item has been in progress since 10 August, is still open, and the selected period is today
- **THEN** it is counted today, because its interval is extended to the reading clock

#### Scenario: An item whose work fell outside the period is not counted
- **WHEN** a work item's only in-progress interval ran in April and the selected period is June
- **THEN** it is not counted, even if the item was still open in June

#### Scenario: Gaps in the interval are respected
- **WHEN** a work item was in progress from 1 to 3 June and again from 20 to 22 June, and the selected period is 10 to 15 June
- **THEN** it is not counted, because no in-progress interval overlaps that period

#### Scenario: An interval metric's past periods do not move
- **WHEN** a work item in progress during June is completed in August and June is read afterwards
- **THEN** June still counts it, and the June value is the same before and after the completion

#### Scenario: The item list matches the counted set
- **WHEN** the openable list is requested for an interval metric in a period
- **THEN** it lists exactly the items the displayed value counted

## MODIFIED Requirements

### Requirement: Values are bucketed by frequency with correct-polarity evolution
The system SHALL bucket events by Diário, Semanal (ISO week, Monday start), or Mensal
in UTC, and SHALL report each metric's evolution versus the immediately previous
bucket using the metric's `direction`, so that a real improvement reads positive even
when the raw number decreased. When the bucket being computed is the one still in progress, the
comparison SHALL use the same elapsed slice of the previous bucket rather than the previous full
bucket; when the bucket being computed has already ended, the comparison SHALL use the previous
bucket in full.

Which bucket an event falls into SHALL follow the metric's declared occupation of time: by the
event's own date for an instant metric, and by interval overlap for an interval metric. An interval
metric is therefore **not additive across buckets** — an item in progress on three days of a week is
counted once in that week and once in each of those days, so the week is not the sum of its days.

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

#### Scenario: An interval metric's week is not the sum of its days
- **WHEN** one work item was in progress on three days of the same week
- **THEN** it counts once in the week and once in each of the three days
