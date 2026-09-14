## 1. Adapter Azure DevOps (listagem + detecção de stage)

- [x] 1.1 Criar `AdoRepositoryDiscoveryPort` (outbound, em `application.port.outbound`): `discover(String accessToken, String organization, String project)` devolvendo a lista de repositórios do projeto, cada um com um `suggestedProductionStage` opcional.
- [x] 1.2 Implementar em `adapter-out-ado`: listar `{org}/{project}/_apis/git/repositories` (mesmo padrão `normOrg`/`enc` já usado no resto do módulo).
- [x] 1.3 Para cada repositório listado, buscar seus builds recentes do projeto (reaproveitando a mesma filtragem por `repository.name` já usada em `fetchDeploys`), ler o Timeline de cada um, coletar os nomes de stage distintos que passam em `AdoMapper.matchesProduction(stage, "")`, e sugerir o stage só quando sobrar exatamente um candidato.
- [x] 1.4 Testes unitários (fixtures JSON, no padrão de `AdoMapperTest`): repositório com um candidato único de stage, com zero, com mais de um (ambíguo) — os três casos, e a listagem básica.

## 2. Aplicação (caso de uso de descoberta)

- [x] 2.1 Criar `AdoDiscoveryUseCase` (inbound): `start(String organization, String project)` devolvendo `Session(sessionId, DeviceCodePrompt)` (mesmo shape de `AdoSyncUseCase.Session`); `status(String sessionId)` devolvendo fase/terminado/erro e, quando terminado, o diff (`toInsert`/`toRemove`).
- [x] 2.2 Implementar `AdoDiscoveryService`: autentica via `AdoAuthPort` (mesmo fluxo do sync), roda em background (mesmo `Executor` do sync), chama `AdoRepositoryDiscoveryPort.discover(...)`, compara com `StructureRepositoryPort.findRepositories()` filtrado por (organização, projeto) — case-insensitive por chave — e guarda o diff no estado do job (mesmo padrão de `Map<String, Job>` em memória do `AdoSyncService`).
- [x] 2.3 `apply(String sessionId)`: para cada item a inserir chama `RepositoryUseCase.register(...)` (sem time, com o stage sugerido ou vazio); para cada item a remover chama `RepositoryUseCase.delete(...)`; falha se o diff ainda não terminou ou já foi aplicado (idempotência: aplicar duas vezes não duplica nem re-remove).
- [x] 2.4 Testes unitários de `AdoDiscoveryService`: diff correto (insert/remove/escopo por organização+projeto), aplicação idempotente, aplicação antes de terminar é rejeitada.

## 3. Web (endpoints)

- [x] 3.1 Novo controller (ou método em `RepositoryController`) com `POST /api/admin/repositories/discover` (organização, projeto → sessão + prompt), `GET /api/admin/repositories/discover/status?sessionId=` (fase/diff), `POST /api/admin/repositories/discover/apply?sessionId=`.
- [x] 3.2 DTOs novos (`DiscoveryStartDto`, `DiscoveryStatusDto` com a lista de inserir/remover), mesmo padrão de `AdoSyncDtos`.
- [x] 3.3 Testes de web (MockMvc): 200 nos três endpoints, sessão desconhecida devolve 404, aplicar diff ainda não terminado devolve erro.
- [x] 3.4 Atualizar `docs/api/openapi.yaml` com os três endpoints e os schemas novos.

## 4. Composition root

- [x] 4.1 Wiring do `AdoDiscoveryUseCase` no bootstrap, reaproveitando o mesmo `AdoAuthPort`/`Executor` já injetados pro sync existente.

## 5. UI (aba Repositórios)

- [x] 5.1 Campo de organização/projeto + botão "Descobrir repositórios" na aba Repositórios, reaproveitando o componente visual de login device-code já usado no sync (mesmo texto, mesmo polling).
- [x] 5.2 Tela de diff: lista "a inserir" (com o stage sugerido, quando houver) e "a remover", com um botão único "Aplicar" (tudo-ou-nada, conforme design).
- [x] 5.3 Após aplicar, recarregar a lista de repositórios (mesma função já usada após qualquer mutação na aba) — os novos aparecem sem time, prontos pro seletor de time por linha que já existe hoje.
- [x] 5.4 Tratar erro de autenticação/listagem sem quebrar o resto da aba (mesmo padrão de tratamento de erro já usado no sync).

## 6. Fechamento

- [x] 6.1 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
