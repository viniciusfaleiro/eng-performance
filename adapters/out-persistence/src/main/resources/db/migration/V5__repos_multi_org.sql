-- S10 (repos multi-org) · repositórios cadastrados 1-a-1, cada um com sua organização ADO e regra
-- de stage de produção. A org única + PAT da config ADO deixam de existir (ingestão é por repo).

-- Pre-existing rows (dev fixtures) get the sample org so the required-org invariant holds; the
-- default is then dropped so new repositories must be registered with an explicit organization.
ALTER TABLE repository ADD COLUMN organization VARCHAR(500) NOT NULL DEFAULT 'minhaorg';
ALTER TABLE repository ADD COLUMN production_stage VARCHAR(500);
ALTER TABLE repository ALTER COLUMN organization DROP DEFAULT;

ALTER TABLE ado_integration DROP COLUMN organization_url;
ALTER TABLE ado_integration DROP COLUMN pat_secret;
ALTER TABLE ado_integration DROP COLUMN production_stage_rule;
