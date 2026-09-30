## MODIFIED Requirements

### Requirement: The explanation states the rule, the source and the boundaries
An explanation SHALL state, in business language: the rule used to compute the value, which event
it is derived from, which events are counted, which are deliberately left out, and the statistic
applied. It SHALL also state the period the value covers and that a period keeps the team a person
belonged to at the time of the event.

An explanation SHALL additionally state **how the item is placed in time** — which date puts it in
the selected period, or, for a metric counted by interval, that the item is placed by the period its
work overlaps. Without it, two computations that differ entirely read as the same sentence: "items in
progress in the period" describes both "items whose last board change fell in the period" and "items
whose work overlapped the period", and the second is not what the first computes.

An explanation SHALL NOT describe behaviour the computation does not have. The explanation text and
the rule that produces the number SHALL be changed together, and a metric whose calculation changes
SHALL have its explanation reviewed in the same change — the text is a promise to the reader, and a
stale promise is worse than a missing one because nothing signals that it should be doubted.

#### Scenario: Reading an explanation
- **WHEN** a user opens the explanation of a delivery metric
- **THEN** it states the originating event, the population counted, what is excluded, and the
  statistic used to aggregate

#### Scenario: Attribution is explained
- **WHEN** a user opens the explanation of any metric attributed to people
- **THEN** it states that the value stays with the team the person belonged to when the event
  happened

#### Scenario: Placement in time is explained
- **WHEN** a user opens the explanation of a metric derived from work items
- **THEN** it states which date or interval puts an item inside the selected period

#### Scenario: A long-running item's duration is placed explicitly
- **WHEN** a user opens the explanation of a metric measured over completed items, such as Cycle Time
- **THEN** it states that the whole duration is attributed to the period in which the item was completed, even if the work spanned earlier periods

#### Scenario: An interval metric says it is not additive
- **WHEN** a user opens the explanation of a metric counted by interval overlap
- **THEN** it states that the same item can be counted in several periods and that a month is therefore not the sum of its days
