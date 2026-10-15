## ADDED Requirements

### Requirement: The heatmap applies the filter only where it is meaningful, and says so
The comparison heatmap SHALL apply the active work-type selection to its filterable columns (Fluxo
and IA) and SHALL leave the deploy-based DORA columns computed over their whole population. The
columns that ignore the filter SHALL be **visually marked** as not filtered, with the reason
available on the screen, so a reader never compares two populations believing they are one.

#### Scenario: Fluxo and IA columns are filtered
- **WHEN** the heatmap is shown with the bug type selected
- **THEN** each Fluxo and IA cell is computed over the row's bug work

#### Scenario: DORA columns are marked as unfiltered
- **WHEN** the same heatmap is shown
- **THEN** the DORA columns are computed over everything and are marked as not filtered

#### Scenario: Nothing is silently mixed
- **WHEN** a work type is selected and the heatmap is read
- **THEN** the screen states that the marked columns are not affected by the filter
