## Context

S9 shipped interactive Azure DevOps ingestion but assumed a single configured org (`organizationUrl`)
whose repos are all listed and mapped. Real teams span many orgs, and listing a whole org is
unviable. The loader is an admin with access to all orgs and authenticates with device-code (a user
token that already spans every org they can see). So the unit of configuration becomes the
**registered repository**, each carrying its own org, and the sync walks that list — no org config,
no PAT.

## Goals / Non-Goals

**Goals:**
- Let an admin register repos one by one (org + project + repo → team + production stage) via screen
  and API, and have the sync ingest exactly those, across any orgs.
- Keep DORA (deploys) and the work-type distribution (boards) working, even though pipelines and
  work items are project-scoped in ADO, not repo-scoped.
- Remove the single Org URL and the PAT.

**Non-Goals:**
- No org-wide auto-discovery of repos (explicitly rejected as unviable).
- No unattended sync (still admin-triggered, device-code).
- No change to the read side (dashboards).

## Decisions

- **`Repository` is self-describing:** `Repository(organization, project, key, teamId,
  productionStage)`. `organization` is the ADO org (URL or short name); `productionStage` is the rule
  (stage/environment name or regex) that marks a pipeline run as a production deploy for this repo.
  1-repo→1-team is unchanged; `key` identifies the repo within its project.
- **Register one by one, no discovery.** `StructureRepositoryPort` gains create + delete; the admin
  screen has a create form and per-row edit (team, stage) and delete. `POST /api/admin/repositories`
  creates; the existing map-to-team endpoint extends to org/stage; a delete endpoint removes.
- **The sync reads registered repos, not an org.** `AdoEventSourcePort.fetchSince` drops the org-URL
  parameter; the adapter injects `StructureRepositoryPort` and iterates `findRepositories()`:
  - **PRs + commits** are fetched per repo from `{org}/{project}/_apis/git/repositories/{key}/…`.
  - **Pipelines (deploys)** are fetched once per distinct `(org, project)` among registered repos;
    each build's **source repository** (`repository.name`) maps the deploy to a team, and the run is
    classified as production by *that repo's* `productionStage` rule. Runs whose source repo is not
    registered are skipped.
  - **Work items (boards)** are fetched per distinct `(org, project)` and are person-scoped (assignee
    → identity), independent of repo mapping.
  This keeps deploys per-repo (via the build's source repo) so DORA attribution and the per-repo
  production rule both hold, while pipelines/boards are still fetched at their real project scope.
- **Remove Org URL + PAT.** `AdoIntegration` keeps only what's still meaningful (e.g. a last-sync
  marker); `organizationUrl` and `patSecret` are dropped from the record, the config port, the Admin
  screen, and the seeder. Auth stays device-code; the production rule now lives on each repo.

## Risks / Trade-offs

- [A project with several registered repos/teams] → deploys attribute by the build's *source repo*,
  so each deploy lands on the right team; a build with no source repo (or an unregistered one) is
  skipped rather than misattributed. Covered by a mapping test.
- [Duplicate project fetches] → group registered repos by `(org, project)` and fetch pipelines/work
  items once per group; PRs/commits stay per repo.
- [Dropping org/PAT columns] → an additive migration adds the repository columns and drops the ADO
  org/PAT columns; older rows get a null org until re-registered (the seeder rewrites samples).
- [Live multi-org behavior] → the fetch paths are verified against a real org during acceptance, as in
  S9; mapping is fixture-tested.

## Migration Plan

Additive Flyway migration: `repository` gains `organization` and `production_stage`; the
`ado_integration` `organization_url`/`pat_secret` columns are dropped. Reseed sample repos with orgs.
Rollback = revert; the read side is unaffected.

## Open Questions

- Whether `organization` is stored as a full URL (`https://dev.azure.com/org`) or a short name — the
  adapter normalizes both; the screen accepts either. Defaulted to accepting either.
