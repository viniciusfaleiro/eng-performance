## Context

S3 built the engine and S4–S7 the aggregate views, all rolling up over the org tree. The
individual panel is different: it is a **single person's** contribution, most of which is not
a hierarchy roll-up but a direct read of that person's raw events — a daily commit calendar,
review tallies, a work-type split. The PRD scopes it strictly to coaching: only the person's
own/managing account (or an admin) may see it, and the person is never compared to peers. The
engine already attributes person-scoped events (identity → Person, as-of-event) and the S2
`AccessScope.canViewIndividual` already gates `p:` nodes; S8 composes on top of both.

## Goals / Non-Goals

**Goals:**
- Make the individual screen real: contribution calendar, PR assertiveness, delivery trends,
  code-review contribution, work-type distribution (with hours), and an activity drawer with
  Azure DevOps deep-links.
- Reuse the delivery metrics and person attribution; add only the raw data the panel needs.
- Enforce coaching-only access end to end.

**Non-Goals:**
- No aggregation of individual contribution into any ranking or cross-team comparison (PRD).
- No real review/PR-outcome ingestion from ADO — that arrives with S9; the seed fabricates it
  deterministically behind the same event model the adapter will fill.
- No new persistence schema; the new fields live in `detail` and the new event type is an enum
  value.

## Decisions

- **A dedicated `IndividualDashboardService`, not the catalog roll-up.** The calendar, review
  tallies and type split are per-person reads that don't fit `median/ratio/snapshot` over a
  subtree. The service takes the structure and event ports plus the `MetricsQueryUseCase`
  (for the delivery series it can reuse) and a `Clock`, mirroring `MetricsService`. It resolves
  the person's committer identities once and filters raw events by them.
- **REVIEW is a new event type carrying both sides.** A REVIEW event's `committerIdentity` is
  the reviewer; `detail.decision` is approved/changes-requested; `detail.comments` is a count;
  `detail.author` is the reviewed PR's author identity. "Reviews given" filter by reviewer =
  the person's identities; "reviews received" filter by `detail.author` = the person's
  identities. This keeps the two directions in one event and reuses identity→Person resolution
  for both. Alternative (deriving reviews from PR events) can't express who reviewed whom.
- **PR assertiveness is computed in the service, not the catalog.** `flow_efficiency` is
  already a RATIO on PR events reading `detail.num/den`, and the engine's RATIO path reads those
  same keys, so a second RATIO-on-PR metric would collide. Assertiveness is instead computed
  directly over the person's PR events (count with `detail.first_pass=1` over the total) —
  person-scoped, higher-is-better, only ever shown on the individual panel.
- **Delivery trends reuse the person-scoped series.** Throughput, cycle time and %-with-AI come
  straight from `MetricsQueryUseCase.series(metric, "p:…", freq)` — identical to the numbers the
  person would see elsewhere.
- **Calendar and type split are direct reads.** The calendar is the count of the person's COMMIT
  events per day over the last 12 months; the type split groups the person's WORKITEM events by
  `detail.type`, summing `detail.hours` and computing each type's share. The activity feed is the
  person's most recent COMMIT/PR events with their `detail.url`.
- **Coaching-only access in the web adapter.** The endpoint accepts only a `p:` node and requires
  `canViewIndividual` (403 otherwise) — stricter than `canView`, matching the PRD: an org-wide or
  exec account cannot open an individual panel.

## Risks / Trade-offs

- [A second attribution direction (reviews received by author) diverging from the reviewer path]
  → both directions resolve through the same `StructureIndex` identity→Person map; a service test
  covers a review whose reviewer and author are different people.
- [Calendar spanning a team move] → the calendar is the person's own commits regardless of team,
  so as-of-event membership is irrelevant here; no double counting.
- [Seed-fabricated reviews/outcomes diverging from real ADO] → explicitly a Non-Goal; the event
  model (REVIEW, `first_pass`, `type`/`hours`, `url`) is the contract S9 will fill, so the
  boundary is stable.
- [Reseed required] → one-time dev `TRUNCATE raw_event` as in S5/S6; deploys/DORA/Fluxo/IA
  re-seed identically.

## Migration Plan

No schema change. Dev step: `TRUNCATE raw_event` then restart so the seeder writes REVIEW events
and the new `detail` keys. Rollback = revert; older code ignores the new type/keys.

## Open Questions

- None blocking. Work-item types use a fixed vocabulary
  (feature/bug/tech_debt/maintenance/docs) matching the prototype's legend.
