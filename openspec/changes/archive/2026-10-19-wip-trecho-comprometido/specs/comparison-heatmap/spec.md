## ADDED Requirements

### Requirement: WIP leaves the heatmap outside the daily frequency
The comparison heatmap SHALL omit the WIP column whenever the selected frequency is not daily, for
the same reason it omits volume metrics: a column that cannot be compared is worse than a missing
one, because the matrix's whole affordance is comparison. At the daily frequency WIP behaves like
any other column.

#### Scenario: The WIP column is absent at weekly and monthly
- **WHEN** the heatmap is requested at the weekly or monthly frequency
- **THEN** no WIP column is rendered

#### Scenario: The WIP column is present at daily
- **WHEN** the heatmap is requested at the daily frequency
- **THEN** WIP is one of the compared columns
