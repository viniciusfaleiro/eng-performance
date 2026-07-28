## Why

Every aggregate view is now real (S4–S7). The one screen still fully synthetic is the
**individual panel** — the person-level contribution view the PRD reserves for coaching. It
does not fit the catalog roll-up: it is a commit calendar, a PR-assertiveness gauge,
delivery trends, code-review tallies (given vs received), and a work-type distribution with
hours, plus an activity drawer that deep-links to Azure DevOps. S8 makes it real. Because it
compares an individual, it is shown **only** to the person's own/managing account — the S2
coaching rule, already enforced for `p:` nodes.

## What Changes

- **New raw data (seed + `detail`, no schema change):**
  - **REVIEW events** (new `EventType.REVIEW`): a reviewer's action on a PR, carrying the
    decision (approved/changes-requested), a comment count, and the reviewed PR's author —
    so "reviews given" attribute to the reviewer and "reviews received" to the author.
  - **PR outcome**: PR events carry `detail.first_pass` (approved with no changes requested)
    for the assertiveness rate, and `detail.url` for the drawer deep-link.
  - **Work item type & effort**: WORKITEM events carry `detail.type`
    (feature/bug/tech_debt/maintenance/docs) and `detail.hours` for the type distribution.
  - **Commit deep-link**: COMMIT/PR events carry an Azure DevOps `detail.url`.
- **Composed endpoint** `GET /api/individuals/{node}?freq=`: the person's contribution
  calendar (daily commit counts, last 12 months), PR assertiveness (% first-pass approvals),
  delivery trends (throughput, cycle time, %-with-AI — reused person-scoped series), code-review
  stats (comments, approvals given, rejections given, reviews given vs received), the
  work-type distribution (share and hours per type), and the recent activity for the drawer
  (commits/PRs with their ADO links).
- **`pr_assertiveness`** catalog metric (RATIO on PR, `first_pass`, higher-is-better) — reuses
  the engine RATIO path.
- **Coaching-only & scope-enforced**: the endpoint serves a person node only to an admin or
  the managing/own account (`canViewIndividual`, 403 otherwise); the individual is never
  compared with peers.
- **Individual screen** leaves the synthetic mock and reads the endpoint: commit calendar +
  assertiveness gauge, delivery cards/trend, code-review section, type distribution, and the
  activity drawer with real ADO deep-links. Numbers reflect the engine.

## Capabilities

### New Capabilities
- `individual-dashboard`: the person-level contribution panel — the composed endpoint
  (contribution calendar, PR assertiveness, delivery trends, code-review contribution,
  work-type distribution, activity drawer), its coaching-only access rule, and the served
  individual screen.

### Modified Capabilities
- `metrics-engine`: add the **REVIEW event type** to the raw model (a reviewer's decision,
  comment count and the reviewed PR's author), attributable along the person path so reviews
  given attribute to the reviewer and reviews received to the author.

## Impact

- **Modules touched:** `domain` (`EventType.REVIEW`), `application`
  (`pr_assertiveness` catalog entry; `IndividualDashboard*` records + `IndividualDashboardUseCase`
  port + `IndividualDashboardService` composing calendar/assertiveness/delivery/reviews/types/
  activity from raw person events), `adapter-in-web` (`/api/individuals/{node}` + DTOs;
  coaching enforcement; individual screen wired), `bootstrap` (seed REVIEW events, PR outcome,
  work-item type/hours, ADO urls), `architecture-tests` (boundaries hold).
- **Reuses:** S3 engine (person attribution, as-of-event, RATIO, series), the delivery metrics
  (`throughput`/`cycle_time`/`ai_share`), and the S2 `AccessScope.canViewIndividual`.
- **DB:** no schema change (`detail` jsonb holds the new keys; `type` is an enum string, so the
  new REVIEW value needs no DDL). Applying S8 requires a one-time reseed of `raw_event`.
- **No new gates/dependencies**; closes with `./gradlew build` green + individual-screen chrome
  parity. DORA/Fluxo/IA/Comparativo and S1–S7 stay intact.
