# eng-performance

Engineering-performance platform (Azure DevOps → DORA / Flow / AI metrics).
See the product spec in [`initial-spec/initial-spec.md`](initial-spec/initial-spec.md).

This repository currently contains the **engineering harness** plus a **trivial "echo" slice**
that exercises every layer end-to-end. The real backend is built on top of this harness later.

## Stack

- **Java 21**, **Spring Boot 3.4**, **Gradle (Kotlin DSL)** multi-module build.
- **Hexagonal / ports & adapters** architecture, enforced by **ArchUnit**.
- **Server-rendered UI** (Thymeleaf) using **Material Design 3** web components (`@material/web`),
  vendored and served by the service itself.

## Modules

| Module | Responsibility | May depend on |
|---|---|---|
| `domain` | Pure business rules. No framework. | — |
| `application` | Use cases + ports (inbound/outbound). No Spring. | `domain` |
| `adapter-in-web` | HTTP + Thymeleaf/MD3 UI (inbound adapter). | `application`, `domain` |
| `adapter-out-persistence` | Outbound port impls (in-memory for now). | `application`, `domain` |
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
./gradlew build            # compile + all gates (tests, coverage, spotbugs, checkstyle, archunit)
./gradlew spotlessApply    # auto-format
./gradlew :bootstrap:bootRun   # run the app at http://localhost:8080
```

## Frontend (Material Design 3)

MD3 components are vendored into `adapter-in-web/.../static/vendor/md3.js` (no runtime CDN).
Rebuild the bundle after changing which components are used:

```bash
cd frontend
npm install
npm run build      # bundles @material/web -> static/vendor/md3.js
```

## Git hooks

Version-controlled in `hooks/`. Enable them once per clone:

```bash
git config core.hooksPath hooks
chmod +x hooks/*
```

- `pre-commit` → `spotlessCheck` + Checkstyle, plus frontend ESLint/Prettier when `frontend/` changed (fast).
- `pre-push` → full `./gradlew build` + frontend `npm run verify`.

## Echo slice (what runs today)

- UI: `GET /` — MD3 form that calls the API.
- API: `GET|POST /api/echo?message=...` → `{ "text": "...", "sequence": N }`.

The slice flows through every layer: `EchoController` (inbound) → `EchoUseCase` (application) →
`EchoCounterPort` → `InMemoryEchoCounterAdapter` (outbound), with `Message` as the domain invariant.
