## Context

S3 built the on-read metrics engine (attribution scopes, roll-up over the org tree,
coverage, as-of-event, evolution vs. prior period). S4/S5 established the composed
dashboard pattern: a per-group service returns cards (value + evolution + coverage), a
group-specific trend, a coaching-safe children ranking, and group-specific panels, all
node-aware and scope-enforced behind `/api/dashboards/<group>`. The IA group is the third
and final metric group. Its anchor metric `ai_share` (RATIO over COMMIT events, % of
commits marked AI-assisted) already exists in the catalog; the seed already marks commits
AI/not and includes an unlinked `copilot` identity that lowers coverage. The prototype's
IA tab is still synthetic. AI is detected only by a **commit convention** — no external
telemetry (Copilot Metrics API is explicitly out of scope per the PRD).

## Goals / Non-Goals

**Goals:**
- Ship the IA group as real engine data: `ai_share` (reused), `ai_adoption` (new),
  `ai_impact` (new), plus the composed `/api/dashboards/ai` and the wired IA screen at
  chrome parity with the prototype.
- Add exactly two small engine capabilities that the group needs — a distinct-count ratio
  and a population split by an event attribute — without disturbing existing aggregations.
- Give the Admin a screen that documents the AI-detection convention that drives the mark.

**Non-Goals:**
- No external AI telemetry / Copilot Metrics API — detection stays convention-based.
- No new DORA/Fluxo behavior; no schema migration; no per-person public comparison.
- No re-parsing of real commit messages in this slice — the seed derives the AI flag from
  the convention; the real trailer parsing arrives with the ADO adapter (S9).

## Decisions

- **Reuse `ai_share` as the anchor.** It is already a RATIO metric on COMMIT events; the IA
  group only adds two metrics and a composed dashboard, mirroring how Fluxo reused
  `throughput`/`wip`/`pr_review_time`. Alternative (recompute AI share inside the group
  service) was rejected — it would bypass the engine's roll-up/coverage.
- **`ai_adoption` is a distinct-count ratio, a new aggregation.** Adoption = (people with
  ≥1 AI commit in the period) / (people with ≥1 commit in the period), attributed over the
  person population and rolled up the person path (identity → Person → Team-of-record →
  Vertical). Existing RATIO is a volume-weighted sum(num)/sum(den) over events, which
  cannot express "distinct people". We add a `DISTINCT_RATIO` aggregation that counts
  distinct attributed persons matching a predicate over the denominator population.
  Alternative (approximate with event ratios) was rejected as semantically wrong.
- **`ai_impact` is a population split by event attribute.** The same base metric
  (`cycle_time`) is computed over two cohorts of the node's PRs — AI-assisted and not —
  keyed by the event's typed `ai` flag. The engine gains an optional population predicate
  so a metric can be evaluated over the matching subset; the group service calls it twice
  (ai=true, ai=false). The card value is the relative delta (how much faster with AI); the
  panel plots both cohort series. This reuses the S4 `measure` selector idea (choose what
  to read) extended to "choose which events count".
- **The AI flag reuses the existing typed `ai` column, not a new field.** Commits already
  carry it; PR events now set it too (derived from their commits by the convention). No
  Flyway migration and no `detail` key — the `raw_event.ai` column already exists.
- **The convention is reused from platform-config (already built).** The AI-detection
  convention (strategy trailer/tag + detection regex) and its Admin screen already exist
  from S1/S2 (`PlatformConfigPort`, `/api/admin/ai-convention`). S6 only **consumes** it:
  the seed derives each event's AI mark from this convention, so the config stays the
  single source of truth. No change to `platform-config`.
- **Composed `/api/dashboards/ai` follows the S4/S5 DTO shape:** cards, `%-with-AI` trend,
  children adoption ranking, with/without donut, cohort cycle-time series — reusing the
  `AccessScope` filter (403 out of scope; ranking/donut/series over visible children only;
  never people).

## Risks / Trade-offs

- [Distinct-count roll-up double-counting a person who moved teams mid-period] → attribute
  by team-of-record as-of-event (same rule the engine already applies) so each person
  counts once per node subtree; covered by an engine test.
- [`ai_impact` cohort is tiny at leaf nodes → noisy delta] → report coverage alongside and,
  like the other cards, let low coverage speak for itself; no tier is shown. Documented in
  the spec via a coverage scenario.
- [Seed-derived AI flag diverges from real trailer parsing] → explicitly a Non-Goal for
  S6; the convention config is the contract the S9 adapter will honor, so the boundary is
  stable even though the source changes.
- [Reseed required for PR AI flag] → one-time dev `TRUNCATE raw_event` step in tasks, same
  as S5; deploys/DORA re-seed identically.

## Migration Plan

No schema change. Dev step: `TRUNCATE raw_event` then restart so the seeder rewrites PR
events with `detail.ai`. The AI convention is seeded idempotently into platform-config.
Rollback = revert the change; the `detail.ai` key is ignored by prior code.

## Open Questions

- None blocking. The exact set of recognized AI co-author identities is a config value
  (seeded with the prototype's `copilot@github.com`) and can be edited in Admin.
