## MODIFIED Requirements

### Requirement: The waiting metric's drawer presents the impediment breakdown
When the drawer shows the **waiting** metric, it SHALL present the breakdown of that waiting time by
impediment reason **above** the item list, and the parts SHALL add up to the **waiting figure shown
on the board** — the number the reader clicked to get there. Any other total belongs to a different
question and SHALL be labelled as context, never as the sum of the slices.

It SHALL also state how much of the period's population carries an impediment signal at all.
Without that statement a team that simply never marks impediments is indistinguishable from a team
that is never blocked, and the second reading is the flattering one.

The item list below SHALL keep showing each item's **own** waiting hours, which is what makes a
single item checkable; the slices above explain the figure, the list explains the items.

#### Scenario: The breakdown appears with the item list
- **WHEN** a user opens the waiting metric's drawer
- **THEN** the impediment reasons are shown above the list of items considered in the calculation

#### Scenario: The slices sum to the figure that opened the drawer
- **WHEN** a user opens the waiting drawer from a board showing 8 h of waiting
- **THEN** the slices in the drawer sum to 8 h

#### Scenario: The drawer states the signal's coverage
- **WHEN** the waiting metric's drawer is shown
- **THEN** it states how many of the period's items carry an impediment signal
