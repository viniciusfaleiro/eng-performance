## 1. Portas & serviço de sync (module `application`, sem Spring)

- [x] 1.1 Porta outbound `AdoAuthPort`: `beginDeviceCode()` → {userCode, verificationUri, deviceCode, expiresIn, interval}; `poll(deviceCode)` → token|pending|erro
- [x] 1.2 Porta outbound `AdoEventSourcePort`: `fetchSince(watermark, ProgressSink)` → eventos já mapeados para `RawEvent` (Repos/PRs/commits, Pipelines, Boards)
- [x] 1.3 Porta outbound `SyncStatePort`: ler/gravar watermark por fonte + resumo da última sync
- [x] 1.4 Porta inbound `AdoSyncUseCase`: `start()` → device-code + sessionId; `status(sessionId)` → fase/contagens/estado
- [x] 1.5 `AdoSyncService`: orquestra (auth → fetchSince → `EventStorePort.saveAll` upsert → avança watermark → grava resumo) como job assíncrono; registro de sessões em memória; backfill configurável (default 6 meses) na 1ª vez
- [x] 1.6 Testes de aplicação (fakes): 1ª sync = backfill / 2ª = só diff pós-watermark; re-run não duplica (upsert por id); progresso reportado; auth pendente vira concluída

## 2. Adapter ADO (novo module `adapter-out-ado`)

- [x] 2.1 Novo módulo no `settings.gradle.kts` + build (só ele depende do client HTTP/JSON; sem Spring no domínio/aplicação)
- [x] 2.2 `AdoAuthPort` impl: device-code contra o Entra (client `04b07795-…`, scope `499b84ac-…/.default offline_access`, authority `organizations`); refresh via `offline_access` durante o backfill; token só em memória
- [x] 2.3 Client REST do ADO (api-version 7.1) com paginação (continuation token) e respeito a rate-limit
- [x] 2.4 Mapeamento → `RawEvent`: PR (fases/cycle_h/first_pass) + REVIEW (decision/comments/author dos votos e threads); commits (marca de IA pela convenção, url); Pipelines → DEPLOY (outcome/lead/recovery) pela regra de stage de produção; Work items → type/hours
- [x] 2.5 Testes de mapeamento contra **fixtures JSON gravados** do ADO (sem tenant no CI): PR→PR+REVIEW; run→DEPLOY; identidade não vinculada → não atribuída; idempotência de id

## 3. Persistência (module `adapter-out-persistence`)

- [x] 3.1 Migration Flyway `V4__sync_state.sql` (watermark por fonte + resumo/última sync)
- [x] 3.2 `SyncStatePort` impl (JPA) + `EventStorePort.saveAll` idempotente (upsert por id) se ainda não for
- [x] 3.3 Config da regra de **stage de produção** (global + override por time) persistida junto da config ADO
- [x] 3.4 Teste de integração (Testcontainers): watermark grava/lê; upsert não duplica

## 4. Adapter web (module `adapter-in-web`)

- [x] 4.1 `POST /api/admin/ado/sync` (admin-only) → inicia job, devolve device-code + sessionId; `GET /api/admin/ado/sync/status?sessionId` → progresso; DTOs
- [x] 4.2 Enforcement admin-only (403 para não-admin), reusando o gate de admin existente
- [x] 4.3 Testes de web (MockMvc): admin inicia e vê status; não-admin → 403
- [x] 4.4 Frontend: aba **Integração ADO** ganha botão Sincronizar, o prompt de device-code (código + link + copiar), a tela de progresso e "última sync/cobertura"

## 5. Composição & fronteiras (modules `bootstrap`, `architecture-tests`)

- [x] 5.1 Wiring do `AdoSyncUseCase` + adapter ADO; `EventFixtures` gated para dev (desligado quando a integração está conectada)
- [x] 5.2 Regra ArchUnit: só `adapter-out-ado` importa o client HTTP/ADO; domínio e aplicação seguem sem framework
- [x] 5.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates; S1–S8 intactos

## 6. Validação end-to-end (na máquina do admin, fora do CI)

- [ ] 6.1 Conectar a org, `TRUNCATE raw_event` uma vez, clicar Sincronizar, completar login+MFA, ver o backfill concluir e os dashboards refletirem dados reais
- [ ] 6.2 Rodar uma 2ª sync e confirmar que só o diff entra (rápido) e nada duplica
- [ ] 6.3 Vincular identidades/repos novos no Admin e confirmar que a cobertura sobe
