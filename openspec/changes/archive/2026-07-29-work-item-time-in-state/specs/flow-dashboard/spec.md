## ADDED Requirements

### Requirement: WIP measures time spent in progress states
The system SHALL compute **WIP** as the **median time a work item spends in in-progress states** —
the duration in states classified as in-progress (by Azure DevOps state category, per the
ado-integration mapping) — and no longer from the manual `CompletedWork` effort field. WIP SHALL be
reported in hours, lower-is-better, with correct-polarity evolution and coverage for the requested
node and frequency. Work items with **no usable state transition** SHALL be excluded from the value
and reflected in the coverage as "no data" — they SHALL NOT be counted as zero.

#### Scenario: WIP reflects in-progress duration
- **WHEN** the Fluxo dashboard is requested for a node whose work items have recorded state transitions
- **THEN** WIP is the median in-progress time across those items, in hours, with coverage over the items that had usable history

#### Scenario: Items without transitions do not deflate WIP
- **WHEN** some of a node's work items have no usable state transition
- **THEN** those items are excluded from the WIP value and lower the coverage, rather than being counted as zero WIP
