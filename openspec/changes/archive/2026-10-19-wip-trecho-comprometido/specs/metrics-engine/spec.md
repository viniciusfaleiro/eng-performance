## MODIFIED Requirements

### Requirement: An event occupies time either at an instant or over an interval
The metric catalog SHALL declare, per metric, whether its events occupy time **at an instant** or
**over an interval**, and the engine SHALL decide period membership accordingly:

- **Instant** — the event belongs to the period containing its own date. This is how a commit, a
  deploy, a closed pull request and a completed work item are counted, and it stays the default.
- **Interval** — the event belongs to every period its interval overlaps, and its own date is
  irrelevant. An item worked from 5 to 12 June belongs to June, to the week of the 8th and to each
  of those days, whatever date its record carries.

An interval metric SHALL declare **which** interval of the event it reads. An event can carry more
than one — what a person was working on and what the team had taken on are different stretches of
the same item — and a single implicit interval forced every interval metric to answer with the
boundaries of whichever reading recorded it first.

Membership SHALL be decided in **one place**, so that the value, the trend chart and the openable
item list can never disagree about which items a period contains.

An item's interval SHALL be read from the item's own state history. An item that has **not** reached
a terminal state SHALL have its interval extended to the **clock of the reading**, not the clock of
the ingestion that recorded it — otherwise a daily reading reports what the scheduler last saw
instead of what is open, and the number silently depends on when the sync ran.

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

#### Scenario: Two interval metrics read their own intervals
- **WHEN** one metric declares the committed stretch and another the in-progress stretches of the same item
- **THEN** each decides membership by the interval it declared, and the two may contain different periods
