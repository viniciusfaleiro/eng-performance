## Context

S1 gave us the structure (Vertical→Time→Pessoa with as-of-event TeamMembership),
CommitterIdentity and Repository. S2 gave login + `AccessScope`. Nothing measures
activity yet. The prototype's dashboards (DORA/Fluxo/IA) all read the same shape:
a metric value for a **node**, over a **frequency bucket**, with **evolution vs. the
previous period** and correct **polarity**. S3 builds that engine once, generically,
so S4–S6 are just catalog entries + a screen.

All eight design decisions below were locked with the product owner in a design
review (grill). They are the source of truth for implementation.

## Goals / Non-Goals

**Goals:**
- A durable raw-event store and a metric catalog, behind ports (no JPA in domain/app).
- An on-read aggregation engine that is *statistically correct* and *historically
  honest*, covering both attribution paths and all four aggregation types.
- Node-aware metric endpoints enforcing the S2 scope, plus the navigation shell and
  the Tendências view at 0px parity for what this slice ships.
- Deterministic seed so tests and visual parity are reproducible.

**Non-Goals:**
- Composed group dashboards DORA/Fluxo/IA with benchmark tiers/rankings (S4–S6).
- Comparativo/heatmap (S7), detailed individual panel (S8).
- Real Azure DevOps ingestion (S9) — S3 seeds synthetic events behind the same port.
- Pre-materialized/rollup tables or caching — aggregation is on-read for MVP volume.

## Decisions

### 1. Raw event = single polymorphic table
`raw_event`: `id`, `type` (COMMIT|PR|DEPLOY|WORKITEM), `occurred_at` (UTC instant),
`repo_key` (nullable), `committer_identity` (nullable), `numeric_value` (nullable —
hours/lines/1), `phase` (nullable — for cycle-time phases), `is_ai` (bool), `detail`
(jsonb). One `EventStorePort` ingestion path. The S9 ADO adapter only fills these
columns — the "house" (schema + engine) already exists. Rationale: one ingestion
path, trivial to extend; typed-per-kind tables would 4× the mapping for no MVP gain.

### 2. On-read aggregation engine (application layer, no Spring)
Per metric, resolve one of two **attribution paths**:
- `person`: `committer_identity` → CommitterIdentity → Person → (as-of-event) Team → Vertical.
- `repo`: `repo_key` → Repository → Team → Vertical.

**Aggregation types:** `sum`, `median`, `ratio` (weighted = Σnumerators / Σdenominators),
`snapshot` (value at bucket end). **Roll-up** climbs Pessoa→Time→Vertical→all.

**CRITICAL — median/ratio are recomputed over the event population that falls in the
queried node, never composed from children's medians/ratios.** A team's median PR
review time = median over *all* the team's PRs, not the average of per-person medians;
otherwise an 8-PR dev weighs the same as a 2-PR dev. `sum` is naturally composable;
`snapshot` takes the value at the bucket boundary.

**Bucketing:** Diário/Semanal/Mensal. Week = ISO week starting Monday; all instants
in UTC.

**Evolution & polarity:** each metric declares `direction` (higher|lower is better)
and `sentiment` renders green/red by *good/bad*, not by *up/down* (e.g. cycle time
falling = green even though the number went down). Evolution compares the current
bucket to the immediately previous one.

### 3. As-of-event attribution (historical honesty)
An event attributes to the Team/Vertical the Person occupied **on `occurred_at`**,
resolved from the S1 TeamMembership vigência — not the person's current team. Moving
someone between teams does **not** rewrite historical metrics. Tests must cover a
person who changed teams: their old events stay with the old team.

### 4. Unattributed bucket + coverage
Events whose identity/repo doesn't resolve to a person/team go to a **"Não atribuído"**
bucket, **excluded** from team/vertical aggregates. The engine exposes a **coverage %**
(attributed events / total) as a data-quality badge. Rationale: makes a missing link
visible instead of silently shrinking a team's numbers.

### 5. Partial current-period comparison
When the current bucket is in progress, compare the elapsed slice against the **same
elapsed slice** of the previous bucket (Mon–Wed vs. last week's Mon–Wed), not against
the previous *full* bucket — avoids a false "piorou" from an unfinished period.

### 6. Catalog = schema + representative subset
S3 defines the catalog schema (`key`, `label`, `group`, `attributionScope`,
`aggregation`, `unit`, `direction`, `sentiment`) and seeds ONE metric per relevant
aggregation×attribution combination — enough to exercise every code path and populate
Tendências. DORA/Fluxo/IA groups *add* their metrics to the catalog in S4–S6.

### 7. Node-aware endpoints + shell, respecting S2 scope
`GET /api/metrics/catalog`, `GET /api/metrics/cards?node=&freq=`,
`GET /api/metrics/{key}/series?node=&freq=`. Every endpoint runs through the S2
`AccessScope`: 403 for a node outside scope, individuals coaching-only (reusing the
existing filter/guard). The served prototype gains the frequency+view shell and the
Tendências view wired to these endpoints; DORA/Fluxo/IA card grids stay stubbed for
S4–S6. Parity is measured 0px only on the shell + Tendências.

### 8. Deterministic synthetic seed (~6 months)
An idempotent seeder generates ~6 months of events for the S1 people/repos with
**anchored dates** (no `now()`, no randomness) so buckets, tests and screenshots are
stable. Includes at least one person with a mid-window team change (to exercise
as-of-event) and events with no linkable identity/repo (to exercise coverage).

## Risks / Trade-offs

- **On-read cost:** recomputing median/ratio over the population per request is O(events
  in scope). Fine for seeded/MVP volume; if real ADO backfill (S9) makes it slow, add
  a rollup table *behind the port* without touching domain/app. Accepted for now.
- **As-of-event complexity:** resolving membership at `occurred_at` is more work than
  "current team", but it's the whole reason S1 modeled vigência; skipping it would let
  a team move silently rewrite history. Worth the cost.
- **Partial-period comparison edge cases:** first bucket in the seed window has no
  previous slice — evolution is reported as "n/a" rather than a misleading number.
- **Catalog drift:** seeding a subset now means Tendências shows fewer metrics than the
  final product; acceptable because the groups explicitly own their metrics later. The
  `log`/UI must not imply the catalog is complete.
- **Timezone:** fixing UTC + ISO-Monday weeks is a deliberate simplification; per-team
  timezones are out of scope and can come with S9 if ever needed.
