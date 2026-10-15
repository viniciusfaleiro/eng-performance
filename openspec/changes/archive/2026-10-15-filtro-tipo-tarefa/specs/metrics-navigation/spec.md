## ADDED Requirements

### Requirement: The shell carries a work-type selection
The navigation shell SHALL offer a multi-selection **"Tipo de trabalho"** control alongside
frequency and period, defaulting to no selection (everything). The selection SHALL be part of the
navigation state, applying across Dashboard, Tendências, Comparativo and the individual panel, and
SHALL be reflected in the requests the screen makes, with no screen keeping a selection of its own.

#### Scenario: The selection survives navigation
- **WHEN** a reader selects the bug type on the Fluxo dashboard and switches to Tendências
- **THEN** the chart is shown for bug work, with the same selection still displayed in the control

#### Scenario: No selection reads as before
- **WHEN** nothing is selected
- **THEN** every view behaves exactly as it did before the control existed

### Requirement: A selected work type is shareable as a link
The active work-type selection SHALL be part of the shareable link of a view, so a reader who sends
a link sends the reading they are looking at, not a different one.

#### Scenario: The link carries the selection
- **WHEN** a reader copies the link of a filtered view and another authorized reader opens it
- **THEN** the same node, period, frequency and work types are in force

### Requirement: The control is disabled with a reason where it does not apply
The control SHALL be shown disabled, with the reason stated, wherever no metric on screen can be
filtered by work type — the DORA dashboard above all ("deploy pertence ao repositório, não à tarefa")
rather than being hidden. A control that disappears without explanation reads as a bug.

#### Scenario: DORA disables the control
- **WHEN** the DORA tab is shown
- **THEN** the work-type control is disabled and the reason is available on screen

#### Scenario: A disabled control keeps the selection
- **WHEN** the reader selects a work type, moves to the DORA tab and back
- **THEN** the selection was retained and applies again on return
