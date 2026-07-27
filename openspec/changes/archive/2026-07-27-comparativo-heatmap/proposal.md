## Why

With all three metric groups shipped (S4–S6), the cross-cutting **Comparativo** view is
pure composition: one heatmap of a node's children (rows) against every catalog metric
(columns), reading the same engine cards the dashboards already use. The prototype's
Comparativo is still synthetic; S7 turns it into real engine data. It is also the first
view that can surface **people** rows (a manager comparing their own reports), so it
exercises the S2 coaching-safe rule end-to-end for the first time.

## What Changes

- **Composed endpoint** `GET /api/comparison/heatmap?node=&freq=&scope=`: a matrix whose
  **rows are the node's children** and whose **columns are every catalog metric across the
  three groups** (DORA: `deploy_freq`, `lead_time`, `cfr`, `mttr`; Fluxo: `cycle_time`,
  `throughput`, `wip`, `pr_review_time`, `pr_size`, `flow_efficiency`; IA: `ai_share`,
  `ai_adoption`, `ai_impact`) in catalog order. Each cell carries the **real metric value**
  for that child node — reusing the engine cards; `ai_impact` per row is composed the same
  way the IA dashboard composes it.
- **Node-aware rows**: at the overview, teams (default) or verticals (a `scope` toggle);
  within a vertical, its teams; within a team, its people; for a person, the team's
  colleagues.
- **Coaching-safe & scope-enforced** (same rule as S4/S5/S6): 403 when the base node is
  outside the caller's scope; structure rows (teams/verticals) filtered to nodes the caller
  may view; **people rows only for an admin or the managing/own account** (`canViewIndividual`)
  — an exec/org-wide account sees the team aggregate but never its people. `ai_impact` is
  never exposed per person.
- **Relative colouring is presentational**: the best/intermediate/worst shading per column
  (relative to the peers shown) is computed client-side; the endpoint returns only real
  values and the metric order/labels.
- **Comparativo screen** leaves the synthetic mock and reads `/api/comparison/heatmap`,
  keeping the same heat table (DORA/Fluxo/IA group headers, the Todos os times / Todas as
  verticais toggle at the overview, the melhor/intermediário/pior legend). Numbers reflect
  the engine and diverge from the mock by being real.

## Capabilities

### New Capabilities
- `comparison-heatmap`: the cross-cutting Comparativo heatmap — the node-aware children ×
  all-metrics matrix endpoint, its coaching-safe/scope-enforced row selection (people only
  for the managing/own account), and the served Comparativo screen.

### Modified Capabilities
<!-- None: reuses the metrics-engine cards and the S2 access scope unchanged; only a new
     composed read endpoint + screen. -->

## Impact

- **Modules touched:** `application` (`ComparisonHeatmap`/`HeatmapRow` records, a
  `ComparisonHeatmapUseCase` port, `ComparisonHeatmapService` composing per-child cards +
  the composed `ai_impact`), `adapter-in-web` (`/api/comparison/heatmap` + DTOs; scope and
  coaching enforcement; Comparativo screen wired), `architecture-tests` (boundaries hold).
- **Reuses:** S3 engine cards, the S4/S5/S6 catalogs and composed-dashboard pattern, the
  IA `ai_impact` composition, and the S2 `AccessScope` (`canView`, `canViewIndividual`).
- **DB:** no schema change, no new metric, no reseed — the heatmap only reads existing
  events through the engine.
- **No new gates/dependencies**; closes with `./gradlew build` green + Comparativo chrome
  parity. DORA/Fluxo/IA and S1–S6 stay intact.
