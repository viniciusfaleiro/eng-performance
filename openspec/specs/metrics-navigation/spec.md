# metrics-navigation Specification

## Purpose
Node-aware metric endpoints (catalog/cards/series) enforcing the authorization
access scope, the navigation shell (frequency + view), and the Tendências view
wired to the metrics engine. Created by archiving change motor-metricas-shell.

## Requirements

### Requirement: Metric endpoints are node-aware and scope-enforced
The system SHALL expose the catalog, per-node metric cards, and per-metric series
through endpoints parameterized by node and frequency, and SHALL enforce the access
scope from authentication/authorization: a request for a node outside the caller's
scope responds 403, and individual (person) data follows the coaching-only rule.

#### Scenario: Cards are returned for an in-scope node
- **WHEN** an authenticated user requests metric cards for a node within their scope
- **THEN** the cards are returned for that node at the requested frequency

#### Scenario: A node outside scope is denied
- **WHEN** an authenticated user requests metrics for a node outside their scope
- **THEN** the system responds 403

#### Scenario: A peer's individual metrics are denied
- **WHEN** a member requests a teammate's individual metric series
- **THEN** the system responds 403

### Requirement: Navigation shell selects frequency and view
The system SHALL provide a navigation shell that lets the user switch frequency
(Diário/Semanal/Mensal), period, and view, recomputing the displayed metrics for the selected
structure node without reloading the structure tree. The shell SHALL reuse the
prototype's design system and be self-contained (no external CDN).

#### Scenario: Changing frequency recomputes the view
- **WHEN** the user switches the frequency selector
- **THEN** the displayed metrics recompute for the new frequency without reloading the tree

#### Scenario: Selecting a node updates the panel
- **WHEN** the user selects a node in the scope-filtered tree
- **THEN** the panel updates to that node's metrics

#### Scenario: Changing period recomputes the view
- **WHEN** the user changes the selected period
- **THEN** the displayed metrics recompute for that period without reloading the tree

### Requirement: Tendências view renders a metric over time
The system SHALL provide a Tendências view that renders a selected metric's series
over the chosen frequency for the current node, matching the prototype's visual
design for the parts this slice ships (shell + Tendências) at pixel parity.

#### Scenario: Tendências shows the series for the current node and frequency
- **WHEN** the user opens the Tendências view for a node
- **THEN** the selected metric's series is charted over time at the current frequency

#### Scenario: Tendências matches the prototype
- **WHEN** the shell and Tendências view are rendered for an admin
- **THEN** they match the prototype's corresponding screens pixel-for-pixel

### Requirement: The viewed period can be moved into the past
The system SHALL let the user choose which period is displayed, at the selected frequency — a day,
a week or a month — and SHALL recompute every displayed number for that period. The control SHALL
offer stepping one period at a time in both directions, jumping to a recent period from a list, and
returning to the current period. The chosen period SHALL be presented in a form that matches the
frequency.

#### Scenario: Opening a past month
- **WHEN** the frequency is monthly and the user selects a past month
- **THEN** every metric on screen is recomputed for that month, and the comparison is against the
  month immediately before it

#### Scenario: The control follows the frequency
- **WHEN** the user switches frequency while a past period is selected
- **THEN** the period control presents the equivalent period at the new frequency — a day, a week,
  or a month — and the displayed numbers follow

#### Scenario: Stepping back and forth
- **WHEN** the user steps one period back and then one period forward
- **THEN** the view returns to the period it started from

#### Scenario: Returning to the present
- **WHEN** the user activates the shortcut back to the current period
- **THEN** the view shows the current period and the control no longer signals a past period

### Requirement: The selected period applies to the whole navigation
When a past period is selected, the system SHALL apply it to every surface that displays metrics —
dashboard cards, trends, the comparison heatmap, the individual panel and metric drilldowns — so
that no two panels on screen describe different intervals. The trend window SHALL end at the
selected period rather than at the present.

#### Scenario: Trends end at the selected period
- **WHEN** a past period is selected and a trend is displayed
- **THEN** the last point of the series is the selected period

#### Scenario: Drilldown matches the card
- **WHEN** the user opens the drilldown of a card while a past period is selected
- **THEN** the listed items are the ones from that same period

#### Scenario: The heatmap follows the period
- **WHEN** a past period is selected and the comparison heatmap is opened
- **THEN** its cells report the values of that period

### Requirement: A viewed period is shareable as a link
The system SHALL carry the selected node, frequency and period in the address, so that opening the
same address later shows the same period. The address SHALL identify the period absolutely, not
relative to the present.

#### Scenario: Reopening a shared address
- **WHEN** an address captured while viewing a past period is opened on a later day
- **THEN** it still shows that same period

#### Scenario: Address without a period
- **WHEN** an address carries no period
- **THEN** the current period is displayed

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
