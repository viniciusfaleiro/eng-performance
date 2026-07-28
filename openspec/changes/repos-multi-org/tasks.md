## 1. Domínio & portas (modules `domain`, `application`, sem Spring)

- [x] 1.1 `Repository` ganha `organization` e `productionStage` (além de project/key/teamId); mantém 1-repo→1-time e `assignTo`
- [x] 1.2 `StructureRepositoryPort`: `saveRepository` (upsert por key, já existe) + `deleteRepository(key)`
- [x] 1.3 `AdoIntegration` (domínio config) perde `organizationUrl`/`patSecret` (mantém o mínimo: última validação/marca); `PlatformConfigUseCase.saveAdoIntegration` ajustado
- [x] 1.4 `AdoEventSourcePort.fetchSince` perde o parâmetro de org-URL (passa a iterar os repos cadastrados internamente)

## 2. Persistência (module `adapter-out-persistence`)

- [x] 2.1 Migration Flyway `V5`: `repository` ganha `organization` e `production_stage`; `ado_integration` dropa `organization_url` e `pat_secret`
- [x] 2.2 Mapear as novas colunas de `Repository` no adapter JPA; `deleteRepository`
- [x] 2.3 Ajustar o repositório JPA de config (sem org/PAT)

## 3. Adapter ADO (module `adapter-out-ado`)

- [x] 3.1 `AdoEventSource` injeta `StructureRepositoryPort`; itera `findRepositories()` em vez de listar repos de uma org
- [x] 3.2 Por repo: PRs + commits via `{org}/{project}/_apis/git/repositories/{key}/…` (usa a org do próprio repo)
- [x] 3.3 Por `(org, projeto)` distinto: pipeline runs (deploys) e work items uma vez; deploy atribuído ao **source repo** do build → time, classificado pela regra de stage **daquele repo**; run de repo não cadastrado é ignorado
- [x] 3.4 Testes de mapeamento (fixtures): build com source repo registrado → DEPLOY do time certo com stage do repo; source repo não cadastrado → sem deploy; PRs/commits por repo

## 4. Aplicação — sync (module `application`)

- [x] 4.1 `AdoSyncService` não lê mais org da config (só device-code + repos cadastrados); watermark/idempotência intactos
- [x] 4.2 Testes de aplicação (fakes): sync percorre repos de orgs diferentes; sem org configurada não quebra

## 5. Adapter web (module `adapter-in-web`)

- [x] 5.1 `POST /api/admin/repositories` (criar: organization, project, key, teamId, productionStage) + `DELETE /api/admin/repositories/{key}` + estender o PUT de mapeamento p/ org/stage; DTOs
- [x] 5.2 Remover os campos Org URL + PAT dos DTOs/endpoint de config ADO (mantém a convenção de IA e o Sincronizar)
- [x] 5.3 Testes de web (MockMvc): criar repo com org+time (200/201); deletar; config ADO sem org/PAT
- [x] 5.4 Frontend: aba **Repositórios** ganha form de cadastro 1-a-1 (org, projeto, repo, time, stage), editar e remover; aba **Integração ADO** perde Org/PAT (fica só Sincronizar + convenção)

## 6. Composição, seed & fechamento

- [x] 6.1 Wiring do `AdoEventSource` com o `StructureRepositoryPort`
- [x] 6.2 Seed: repos de exemplo com `organization`/`productionStage`; remover org/PAT do seed de config
- [x] 6.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates; S1–S9 intactos (dashboards inalterados)

## 7. Aceite (na máquina do admin, fora do CI)

- [ ] 7.1 Cadastrar repos de 2 orgs diferentes, mapear a times, definir stage; Sincronizar (device-code) e ver PRs/commits/deploys/work-items entrarem só desses repos
