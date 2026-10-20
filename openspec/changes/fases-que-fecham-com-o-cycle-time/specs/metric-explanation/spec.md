## MODIFIED Requirements

### Requirement: The impediment breakdown has its own explanation
The breakdown of waiting time by impediment reason SHALL carry an explanation of the same shape as
every other, describing where a reason comes from, what the "no reason" bucket means, what the
residual means, that a stretch under several reasons is split among them, and that the parts add up
to the waiting figure shown on the board.

#### Scenario: The breakdown is explainable
- **WHEN** a reader opens the information affordance on the impediment breakdown
- **THEN** an explanation is shown covering the source of the reasons, the "no reason" bucket, the residual, the splitting of simultaneous reasons, and the fact that they sum to the waiting figure on the board

## ADDED Requirements

### Requirement: The phase explanations state the population and the composition rule
The explanation of the cycle-time phases, and of each phase metric, SHALL state **which items are
counted** — completed items that entered a working state, with items closed without ever being
worked left out because they have no cycle — and **how the value is formed**: the share that phase
took of the counted items' time, applied to the Cycle Time shown above it, which is why the three
add up to the card.

A reader who cannot tell whether a phase is a median, a sum or a share has no way to judge whether
the phases disagreeing with the card is a bug or arithmetic, and that was exactly the doubt the
previous texts left.

#### Scenario: The explanation states the population
- **WHEN** a reader opens the explanation of the phase breakdown
- **THEN** it states that items closed without entering a working state are not counted, and why

#### Scenario: The explanation states why the phases sum to the card
- **WHEN** a reader opens the explanation of the phase breakdown or of a phase metric
- **THEN** it states that each phase is that phase's share of the counted items' time applied to the Cycle Time
