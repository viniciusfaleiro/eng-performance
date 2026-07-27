## Why

With the engine (S3) and the DORA/Fluxo dashboard pattern (S4/S5) in place, the IA group
is mostly composition — a new metric group + a composed dashboard, reusing the RATIO
path, the roll-up, the coaching-safe ranking, and scope enforcement already built. The
prototype's IA dashboard is still a stub; S6 turns it into real engine data and answers
the product's anchor question: **"is AI helping, and who has adopted it?"** AI is
detected purely by a **commit convention** (no external telemetry), so the Admin also
gains the screen that documents that convention.

## What Changes

- **AI share (reuse)**: `ai_share` = % of commits marked AI-assisted already exists in the
  catalog (RATIO on COMMIT events); the IA group surfaces it as its anchor card and trend.
- **AI adoption (new metric)**: `ai_adoption` = % of the node's people who made at least
  one AI-assisted commit in the period (distinct people-with-AI over the node's active
  people). This needs a new engine aggregation: a **distinct-count ratio** over the person
  population (not a per-event sum/ratio).
- **AI impact (new metric)**: `ai_impact` compares the cycle time of PRs **with AI** vs
  **without AI**. This needs a new engine capability: **splitting a metric's population by
  an event attribute** (the AI flag) so the same base metric is computed over two cohorts.
  The seed marks each PR as AI-assisted or not (derived from its commits via the
  convention); commits already carry the AI mark.
- **Composed endpoint** `GET /api/dashboards/ai?node=&freq=`: the IA cards (`ai_share`,
  `ai_adoption`, `ai_impact` — value + evolution + coverage, no DORA tier), the % -with-AI
  trend, the adoption ranking of the node's children, the with/without-AI donut, and the
  cycle-time with-AI-vs-without-AI series — node-aware and scope-enforced.
- **Ranking/donut/series** compare structures only (children of the node), never people —
  the same coaching-safe rule as the DORA/Fluxo comparisons.
- **AI convention (reuse)**: the platform-config convention that marks a commit as
  AI-assisted (trailer/tag + detection regex) and its Admin screen **already exist** (S1/S2);
  S6 consumes that convention to derive the AI mark in the seed — no change to
  `platform-config`.
- **IA screen** leaves the stub and reads real engine data (cards, %-with-AI trend,
  adoption ranking, donut, with/without-AI series).
- **Seed** evolves PR events to carry an AI flag (from their commits by the convention);
  commit AI marks already exist.

## Capabilities

### New Capabilities
- `ai-dashboard`: the IA metric group (AI share, AI adoption, AI impact), the composed IA
  dashboard endpoint (cards + %-with-AI trend + adoption ranking + with/without-AI donut
  and cycle-time comparison), and the coaching-safe comparison rule.

### Modified Capabilities
- `metrics-engine`: add a **distinct-count ratio** aggregation (people-with-AI over active
  people) and a **population split by event attribute** (compute a base metric over the
  AI and non-AI cohorts of the same node).

## Impact

- **Modules touched:** `application` (catalog adds `ai_adoption`/`ai_impact`; engine adds
  distinct-count ratio + attribute split; `AiDashboardService` for cards + trend + ranking
  + donut + with/without series), `adapter-in-web` (`/api/dashboards/ai` + DTOs; IA screen
  wired), `bootstrap` (PR seed carries AI flag derived from the existing convention),
  `architecture-tests` (boundaries hold). The AI-convention config + Admin screen already
  exist (S1/S2) and are reused unchanged.
- **Reuses:** S3 engine (RATIO, roll-up, coverage, as-of-event), S4/S5 pattern (composed
  dashboard, children ranking, scope enforcement), existing `ai_share` and `cycle_time`.
- **DB:** no schema change (`detail` jsonb holds the PR AI flag; convention stored in the
  existing platform-config table). Applying S6 requires a one-time reseed of `raw_event`
  (dev step, in tasks) so PR events carry the AI flag.
- **No new gates/dependencies**; closes with `./gradlew build` green + IA-screen chrome
  parity. DORA/Fluxo and S1–S5 stay intact.
