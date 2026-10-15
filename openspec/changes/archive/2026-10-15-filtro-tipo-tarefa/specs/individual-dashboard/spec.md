## ADDED Requirements

### Requirement: The individual panel responds to the work-type filter
The individual panel SHALL apply the active work-type selection to its delivery series, its
verifiable item lists and its activity feed, so a manager can coach on one kind of work at a time.
Sections computed from events that carry no work type SHALL be reported as not filtered, on the
same terms as any other unfilterable reading.

#### Scenario: Delivery series follow the selection
- **WHEN** a person's panel is read with technical debt selected
- **THEN** throughput and cycle time are computed over that person's technical-debt work

#### Scenario: Item lists match the figures above them
- **WHEN** a filtered delivery figure is opened item by item
- **THEN** the list contains exactly the items of the selected types that produced it

### Requirement: The work distribution is never filtered, and switches the filter on
The work-type distribution SHALL always be computed over the whole period, regardless of the active
selection: it answers how the period divided, and filtering it would always return a single slice
at 100%. Selecting a slice SHALL instead **apply** that work type as the filter for the rest of the
panel and the rest of the navigation.

#### Scenario: The distribution keeps its shape under a filter
- **WHEN** a work type is selected and a person's panel is shown
- **THEN** the distribution still shows all five categories over the whole period

#### Scenario: A slice turns the filter on
- **WHEN** the reader selects the bug slice of the distribution
- **THEN** the bug work type becomes the active selection and the rest of the panel is recomputed under it
