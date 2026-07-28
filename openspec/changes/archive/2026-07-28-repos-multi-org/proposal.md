## Why

Teams work across **multiple Azure DevOps organizations**, so a single configured org can't drive
ingestion, and bulk-loading every repo of an org is unviable. The loader (an admin) has access to
all orgs, authenticates interactively (device-code), and wants to **register repositories one by
one** — each with its org/project — and map it to a team, from a screen and the API. The single
Org URL and the PAT no longer make sense and are removed.

## What Changes

- **A repository is self-describing across orgs.** `Repository` gains an **organization** (its ADO
  org) and a **production-stage rule** (per repo), alongside its project, key and owning team. The
  1-repo→1-team rule stays.
- **Register repos one by one (screen + API).** Admin → Repositórios gains a create form
  (organization, project, repository, team, production stage) backed by `POST /api/admin/repositories`
  (plus edit team/stage and delete). No org-wide discovery.
- **Sync iterates the registered repos**, not one org's repo list:
  - per repo → its **PRs and commits** (via the repo's org/project/key);
  - grouped by (org, project) → that project's **pipeline runs** (deploys, attributed to their source
    repo → team, classified by that repo's production-stage rule) and **work items** (person-scoped);
  - device-code auth unchanged (the user's token spans all their orgs).
- **Remove Org URL + PAT.** Drop `organizationUrl` and `patSecret` from the ADO integration config
  and the Admin → Integração ADO screen; keep the Sincronizar flow. Production-stage config moves
  from a single global rule to per-repo.

## Capabilities

### Modified Capabilities
- `repository-mapping`: a repository carries its **organization** and **production-stage rule**, and
  is **registered one by one** (create/edit/delete) and mapped to a team — no org-wide discovery.
- `ado-integration`: ingestion iterates the **registered repositories across any orgs** (PRs/commits
  per repo; pipelines/work-items per derived project) with **no single configured org and no PAT**.

## Impact

- **Modules touched:** `domain` (`Repository` + organization + productionStage), `application`
  (`StructureRepositoryPort` create/delete; `AdoEventSourcePort` reads registered repos instead of an
  org URL; `AdoSyncService`/config drop the org), `adapter-out-persistence` (migration: repository
  gains `organization` + `production_stage`; ADO config drops org/PAT), `adapter-out-ado`
  (`AdoEventSource` iterates registered repos, groups by project for pipelines/boards), `adapter-in-web`
  (repository CRUD endpoints + screen; drop org/PAT fields from the ADO screen), `bootstrap` (wiring;
  seed sample repos with orgs).
- **Reuses:** the device-code auth, the RawEvent mapping, the repo→team attribution, and the whole
  read side (dashboards unchanged).
- **DB:** additive migration (repository columns); the `ado_integration` org/PAT columns are dropped
  or ignored. One-time reseed of sample repos.
- **No new gates/dependencies**; closes with `./gradlew build` green. Live multi-org sync remains the
  admin's acceptance step.
