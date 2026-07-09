# eng-performance

Engineering-performance platform (Azure DevOps → DORA / Flow / AI metrics).
See the product spec in [`initial-spec/initial-spec.md`](initial-spec/initial-spec.md).

This repository currently contains the **engineering harness** plus a **trivial "echo" slice**
that exercises every layer end-to-end. The real backend is built on top of this harness later.

## Stack

- **Java 21**, **Spring Boot 3.4**, **Gradle (Kotlin DSL)** multi-module build.
- **Hexagonal / ports & adapters** architecture, enforced by **ArchUnit**.
- **Server-rendered UI** (Thymeleaf) using the **prototype's own design system**
  (`prototype/` is the visual spec) — plain CSS + vanilla JS, self-contained, no CDN.
  (The earlier `@material/web`/MD3 choice was dropped in favor of reusing the prototype.)

## Modules

| Module | Responsibility | May depend on |
|---|---|---|
| `domain` | Pure business rules. No framework. | — |
| `application` | Use cases + ports (inbound/outbound). No Spring. | `domain` |
| `adapter-in-web` | HTTP + Thymeleaf UI (inbound adapter). | `application`, `domain` |
| `adapter-out-persistence` | Outbound port impls — PostgreSQL (JPA + Flyway). | `application`, `domain` |
| `bootstrap` | Executable app; composition root wiring ports→adapters. | all |
| `architecture-tests` | ArchUnit rules guarding the boundaries. | all (test) |

## Quality gates (all wired into `./gradlew build`)

- **Spotless** (google-java-format) — formatting is the single source of truth.
- **Checkstyle** — curated structural/style rules (`config/checkstyle`).
- **SpotBugs + FindSecBugs** — static & security analysis (`config/spotbugs`).
- **JaCoCo** — coverage reports everywhere; **70% line + 60% branch floor** enforced on `domain` + `application`.
- **ArchUnit** — hexagonal boundaries fail the build if violated.

Frontend (`frontend/`) has its own gate — **ESLint + Prettier + bundle build** (`npm run verify`),
enforced by the git hooks. Run `npm --prefix frontend ci` once to enable it. Security supply-chain
scanning (dependency CVEs, secrets) is intentionally out of scope for this harness.

## Common commands

```bash
docker compose up -d db    # local PostgreSQL (required to run the app; data persists in a volume)
./gradlew build            # compile + all gates (Testcontainers spins up Postgres for the DB test)
./gradlew spotlessApply    # auto-format
./gradlew :bootstrap:bootRun   # run the app at http://localhost:8080
```

Persistence is PostgreSQL (JPA + Flyway migrations in `adapter-out-persistence`), seeded with
fixtures on first start. Integration tests use Testcontainers. This host's Docker daemon is very
new (API floor 1.40); `scripts/fix-docker-min-api.sh` lowers it to 1.24 so Testcontainers connects.

## Frontend (prototype design system)

The UI reuses the prototype's own design system (see `prototype/`): plain CSS +
vanilla JS, server-rendered by Thymeleaf, fully self-contained (no runtime CDN).
The `frontend/` npm workspace bundles/minifies the served assets:

```bash
cd frontend
npm install
npm run build      # bundles the served JS/CSS assets
```

> Note: `@material/web`/MD3 was dropped in favor of the prototype. The esbuild
> bundling is being repurposed to the prototype assets (tracked in the frontend slices).

## Git hooks

Version-controlled in `hooks/`. Enable them once per clone:

```bash
git config core.hooksPath hooks
chmod +x hooks/*
```

- `pre-commit` → `spotlessCheck` + Checkstyle, plus frontend ESLint/Prettier when `frontend/` changed (fast).
- `pre-push` → full `./gradlew build` + frontend `npm run verify`.

## Echo slice (what runs today)

- UI: `GET /` — a form that calls the API.
- API: `GET|POST /api/echo?message=...` → `{ "text": "...", "sequence": N }`.

The slice flows through every layer: `EchoController` (inbound) → `EchoUseCase` (application) →
`EchoCounterPort` → `InMemoryEchoCounterAdapter` (outbound), with `Message` as the domain invariant.
