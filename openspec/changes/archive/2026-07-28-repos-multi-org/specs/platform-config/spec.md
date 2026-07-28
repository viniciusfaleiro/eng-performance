## REMOVED Requirements

### Requirement: Azure DevOps connection is configured and persisted
**Reason**: The single-org connection no longer fits — teams span many organizations, ingestion is
driven by **per-repository registration** (each repository carries its own organization and
production-stage rule), and auth is interactive **device-code** (no PAT). There is no org URL or PAT
left to persist.
**Migration**: Register repositories (organization, project, key, team, production stage) in
Admin → Repositórios; the Admin → Integração ADO screen keeps only the Sincronizar (device-code)
flow, and the AI-detection convention config is unchanged.
