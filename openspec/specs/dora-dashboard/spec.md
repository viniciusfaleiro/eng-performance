# dora-dashboard Specification

## Purpose
The DORA metric group over the metrics engine: the four DORA metrics (incl. MTTR via
recovery pairing and deploy outcome), benchmark tiers, coaching-safe structure
rankings, and the composed, scope-enforced DORA dashboard endpoint + screen. Created
by archiving change grupo-dora.

## Requirements

### Requirement: The four DORA metrics are available
The system SHALL provide the four DORA metrics computed by the metrics engine:
Deployment Frequency (count of deploys, repo-scoped), Lead Time for Changes (median
lead hours, repo-scoped), Change Failure Rate (failed deploys over all deploys,
repo-scoped), and MTTR (median recovery hours, repo-scoped). Each SHALL report its
value, correct-polarity evolution, and coverage for the requested node and frequency.

#### Scenario: DORA metrics computed for a node
- **WHEN** the DORA dashboard is requested for a node and frequency
- **THEN** Deployment Frequency, Lead Time, Change Failure Rate and MTTR are returned with value and evolution

### Requirement: Deploy outcome drives CFR and MTTR
The system SHALL record each deploy's outcome as `success`, `failed`, or `recovery`.
Change Failure Rate SHALL be the count of `failed` deploys over the count of all
deploys, with a `recovery` deploy counting as a non-failure. A `recovery` deploy SHALL
carry the recovery duration, and MTTR SHALL be the median of those durations.

#### Scenario: CFR counts failed over all deploys
- **WHEN** CFR is computed for a repo with a mix of success, failed and recovery deploys
- **THEN** it equals failed divided by the total number of deploys, with recovery not counted as a failure

#### Scenario: MTTR is the median recovery duration
- **WHEN** a failed deploy is followed by a recovery deploy carrying its recovery hours
- **THEN** MTTR is the median of the recovery durations for the node

#### Scenario: Recovery duration respects as-of-event and coverage
- **WHEN** recovery deploys occur on repositories mapped to teams and some deploys are on unmapped repos
- **THEN** MTTR aggregates only attributed recoveries and the unmapped ones lower coverage

### Requirement: DORA metrics are classified into benchmark tiers
The system SHALL classify each DORA metric value into a benchmark tier
(`ELITE`, `ALTO`, `MEDIO`, `BAIXO`) using global, direction-aware thresholds declared
with the metric. Non-DORA metrics SHALL NOT be classified. Deployment Frequency SHALL
be classified on a normalized deploys-per-day value so the tier is independent of the
selected frequency bucket.

#### Scenario: A fast lead time is Elite
- **WHEN** Lead Time for a node is under 24 hours
- **THEN** it is classified as ELITE

#### Scenario: A poor change failure rate is Baixo
- **WHEN** Change Failure Rate for a node is above 45%
- **THEN** it is classified as BAIXO

#### Scenario: Deployment Frequency tier is frequency-independent
- **WHEN** the same underlying deploy rate is viewed at daily, weekly and monthly frequency
- **THEN** the Deployment Frequency tier is the same because it is normalized per day

### Requirement: Structure rankings never expose individuals
The system SHALL rank the children of the selected node by a DORA metric: at the
overview it ranks verticals, within a vertical it ranks that vertical's teams, and for
a team it produces no ranking. The system SHALL NOT rank people. Entries SHALL be
ordered by the metric value respecting its direction (best first) and limited to a
Top-N, and SHALL include only nodes within the caller's access scope.

#### Scenario: Overview ranks verticals
- **WHEN** the ranking is requested at the overview node
- **THEN** the verticals are ordered best-first by the chosen DORA metric

#### Scenario: A vertical ranks its teams
- **WHEN** the ranking is requested for a vertical
- **THEN** that vertical's teams are ordered best-first, and no people appear

#### Scenario: A team produces no ranking
- **WHEN** the ranking is requested for a team
- **THEN** no per-person ranking is produced

### Requirement: The DORA dashboard is composed and scope-enforced
The system SHALL expose a composed DORA dashboard for a node and frequency returning
the four DORA metric cards (value, tier, evolution, coverage) and the ranking of the
node's children, enforcing the access scope (403 for a node outside scope; individuals
coaching-only). The served DORA screen SHALL render this real engine data and match
the prototype's design for the shipped parts at pixel parity.

#### Scenario: Dashboard returned for an in-scope node
- **WHEN** an authenticated user requests the DORA dashboard for a node within their scope
- **THEN** the four cards with tiers and the children ranking are returned

#### Scenario: Out-of-scope node denied
- **WHEN** a user requests the DORA dashboard for a node outside their scope
- **THEN** the system responds 403

#### Scenario: DORA screen chrome matches the prototype
- **WHEN** the DORA dashboard is rendered for an admin
- **THEN** its layout, tier badges, ranking and tier table match the prototype pixel-for-pixel while the numbers reflect the engine
