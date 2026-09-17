## 1. Aplicação — o resultado da ingestão passa a carregar as falhas

- [x] 1.1 Criar `IngestionResult` (eventos + falhas) e `SourceFailure` (fonte + motivo) em
      `application/ado`.
- [x] 1.2 Mudar `AdoEventSourcePort.fetchSince` para devolver `IngestionResult`.
- [x] 1.3 `AdoSyncService`: gravar os eventos coletados; avançar o watermark **apenas** quando não
      houve falha; compor a mensagem final dizendo quantas fontes falharam.
- [x] 1.4 `SyncStatus` passa a expor a lista de falhas.
- [x] 1.5 Testes: sincronização parcial grava eventos e preserva o watermark; sincronização limpa
      avança; falha de login continua abortando.

## 2. Adapter do ADO — isolar a falha por fonte

- [x] 2.1 No laço por repositório, capturar a falha, registrá-la com o contexto
      (`organização/projeto/repo`) e seguir para o próximo.
- [x] 2.2 Mesmo tratamento no laço por projeto, rotulado como projeto.
- [x] 2.3 Logar cada falha em nível de aviso, com a mensagem do Azure DevOps.
- [x] 2.4 Teste: um repositório que responde erro não impede os outros, e aparece na lista.
- [x] 2.5 Teste: um projeto que responde erro não impede os outros.

## 3. Web

- [x] 3.1 O DTO de status de sincronização passa a carregar as falhas.
- [x] 3.2 A tela de Integração ADO lista as fontes que falharam ao final, com o motivo.
- [ ] 3.3 ~~Documentar em `docs/api/openapi.yaml`~~ — não aplicável: os endpoints de
      sincronização do ADO não estão nesse arquivo. Documentá-los seria escopo novo.

## 4. Verificação

- [ ] 4.1 Conferir na tela — **não verificado localmente**: iniciar uma sincronização exige o
      device-code real do Entra, indisponível aqui. O caminho de dados está coberto por testes
      (incluindo checagem por mutação); a renderização da lista foi revisada por código apenas.
- [x] 4.2 Watermark preservado após execução com falha — coberto por
      `aPartialSyncKeepsTheWatermarkSoTheWindowIsRetried`, que falha se a regra for removida.
- [x] 4.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
