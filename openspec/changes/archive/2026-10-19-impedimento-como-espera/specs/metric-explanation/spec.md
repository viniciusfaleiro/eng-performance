## ADDED Requirements

### Requirement: The explanations state the impediment rule where it changes a number
Every explanation whose number the impediment overlay changes SHALL describe the rule: the phase
breakdown, the waiting, active and review phase metrics, the Flow Efficiency and the individual
panel's work distribution. The explanation SHALL say that impeded time is reported as waiting
wherever it occurred, and SHALL tell the team what produces a reason — the tag with the configured
prefix — since the breakdown's usefulness depends on the team writing it.

An explanation that still describes only the board's columns is worse than missing, because the
reader has no way to tell that the rule moved.

#### Scenario: The phase explanation describes the overlay
- **WHEN** a reader opens the explanation of the cycle-time phase breakdown
- **THEN** it states that time the item was impeded is reported as waiting even inside an active column

#### Scenario: Flow Efficiency explains why it dropped
- **WHEN** a reader opens the Flow Efficiency explanation
- **THEN** it states that impeded time counts on the wait side of the ratio

#### Scenario: The team learns what to write
- **WHEN** a reader opens the explanation of the phase breakdown
- **THEN** it states that a tag with the configured prefix is what names the reason in the breakdown

#### Scenario: The individual distribution explains the exclusion
- **WHEN** a reader opens the explanation of the individual work distribution
- **THEN** it states that impeded time is not counted as work

### Requirement: The impediment breakdown has its own explanation
The breakdown of waiting time by impediment reason SHALL carry an explanation of the same shape as
every other, describing where a reason comes from, what the "no reason" bucket means, what the
residual means, and that the parts add up to the waiting figure.

#### Scenario: The breakdown is explainable
- **WHEN** a reader opens the information affordance on the impediment breakdown
- **THEN** an explanation is shown covering the source of the reasons, the "no reason" bucket, the residual and the fact that they sum to the waiting figure
