## 1. Adapter do ADO — histórico de votos

- [x] 1.1 Ler as threads do PR (`/pullRequests/{id}/threads`) e extrair os votos das threads de
      sistema (`CodeReviewThreadType = VoteUpdate`, valor em `CodeReviewVoteResult`).
- [x] 1.2 `first_pass` passa a ser falso quando houve qualquer voto negativo no histórico; e falso
      também quando o histórico não pôde ser lido.
- [x] 1.3 Os eventos `REVIEW` passam a marcar `changes_requested` quando o revisor votou negativo em
      algum momento, mesmo tendo aprovado depois — mantendo um evento por revisor por PR.
- [x] 1.4 Testes: rejeitado→aprovado não é first-pass; aprovado direto é; sem threads não é;
      revisor que rejeitou e aprovou conta como change request.

## 2. Ingestão

- [x] 2.1 Passar o histórico ao mapper a partir do laço de PRs, com uma requisição por PR.
- [x] 2.2 Uma falha ao ler as threads não derruba a coleta do repositório (o PR fica com histórico
      desconhecido), e segue o tratamento de falha por fonte já existente.

## 3. Verificação

- [x] 3.1 Teste ponta a ponta no `AdoEventSource` com um PR rejeitado-e-aprovado.
- [x] 3.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
