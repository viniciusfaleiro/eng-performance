## ADDED Requirements

### Requirement: The IA dashboard responds to the work-type filter
The IA dashboard SHALL accept the work-type selection and apply it to its metrics — AI share, AI
adoption and AI impact — by the work types carried by the commit's or pull request's linked work
items. The AI and non-AI cohorts SHALL both be restricted to the same selection, so the comparison
stays between like and like.

#### Scenario: AI share is read by work type
- **WHEN** the IA dashboard is requested with the bug type selected
- **THEN** AI share is the share of AI-assisted commits among the commits of pull requests linked to bug work

#### Scenario: The impact comparison stays symmetric
- **WHEN** AI impact is computed under a work-type selection
- **THEN** both the AI and the non-AI cohort are restricted to that selection before the cycle times are compared

#### Scenario: The adoption ranking follows the filter
- **WHEN** the adoption ranking of a node's children is produced under a selection
- **THEN** each child's adoption is computed over that selection, and the ranking stays coaching-safe and scope-enforced as before

### Requirement: Filtered IA readings declare their unlinked events
Because the AI signal lives on commits and pull requests, a filtered IA reading SHALL report how
many events were excluded for carrying no work type, and the screen SHALL say so plainly. A low
rate of pull requests linked to work items makes a filtered IA figure unreliable, and the reader
SHALL be able to see that from the dashboard rather than inferring it.

#### Scenario: Exclusions are shown, not swallowed
- **WHEN** a filtered IA reading excludes a large share of the node's pull requests for lack of a link
- **THEN** the dashboard states how many were excluded next to the affected cards
