## ADDED Requirements

### Requirement: The WIP explanation states what the count covers and why it is daily-only
The WIP explanation SHALL state that the count covers the stretch between the team taking an item on
and delivering it — queue included — that **impeded time is excluded**, that **containers are
counted unless the reader filters them out**, and that the figure is offered only at the daily
frequency because it cannot be summed across periods nor compared across periods of different
lengths.

It SHALL also state that the rule applies to what is ingested from now on, so a reader who compares
a recent period with an older one is told why the two were not measured the same way.

#### Scenario: The explanation describes the committed stretch
- **WHEN** a reader opens the WIP explanation
- **THEN** it states that an item counts from commitment to delivery, including time waiting in a queue

#### Scenario: The explanation states the impediment exclusion
- **WHEN** a reader opens the WIP explanation
- **THEN** it states that impeded stretches are not counted

#### Scenario: The explanation says why the metric is daily-only
- **WHEN** a reader opens the WIP explanation
- **THEN** it states that the figure is not additive and is therefore offered only at the daily frequency

#### Scenario: The explanation warns about history
- **WHEN** a reader opens the WIP explanation
- **THEN** it states that periods ingested before the rule changed were measured differently until reprocessed
