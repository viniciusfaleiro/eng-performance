## Context

Hoje o cadastro de repositório é só CRUD manual: `RepositoryController`/`RepositoryUseCase`
(`register`, `mapToTeam`, `delete`) sobre `Repository(key, organization, project, teamId,
productionStage)`, sem nenhuma leitura do ADO real na hora de cadastrar. O sync de eventos
(`AdoSyncUseCase`) já resolve o problema análogo de autenticação — device-code interativo, sem PAT,
via `AdoAuthPort.beginDeviceCode()` + `poll()` — e já expõe pro admin um padrão de UI consolidado:
POST inicia (devolve `userCode`/`verificationUri`), GET `.../status` faz polling até terminar. Vale
reaproveitar esse mesmo padrão pra descoberta, em vez de inventar um novo.

O adapter ADO (`AdoEventSource`/`AdoMapper`) já sabe: montar URL de org normalizada (`normOrg`),
paginar e enumerar builds de um projeto (`_apis/build/builds`), ler o Timeline de um build
(`{id}/timeline`) e classificar um nome de stage como produção via heurística de substring
("prod"/"prd") quando não há regra explícita (`AdoMapper.matchesProduction(stage, "")`). Falta só o
endpoint de **listagem** de repositórios de um projeto (`_apis/git/repositories`), que nenhum fluxo
hoje usa (o sync só opera sobre repos já registrados).

## Goals / Non-Goals

**Goals:**

- Dado (organização, projeto), autenticar (device-code, reaproveitando o fluxo existente), listar
  os repositórios Git reais do projeto, e calcular o diff contra o que já está cadastrado para esse
  mesmo (organização, projeto).
- O admin revisa o diff antes de qualquer inserção/remoção — nada muda no cadastro sem o clique de
  "Aplicar".
- Repositório novo entra sem time (igual ao cadastro manual) e com tentativa de detecção do stage
  de produção; quando ambíguo ou ausente, fica vazio.

**Non-Goals:**

- Descoberta global (várias orgs/projetos de uma vez) — sempre um par por chamada.
- Aplicar o diff automaticamente sem confirmação.
- Apagar `raw_event` histórico ao remover um repositório do cadastro.
- Adivinhar o time de um repositório novo.

## Decisions

### 1. Reaproveitar o padrão start+status do sync existente, não inventar um novo fluxo assíncrono

`AdoDiscoveryUseCase.start(organization, project)` devolve `Session(sessionId, DeviceCodePrompt)` —
mesmo shape de `AdoSyncUseCase.Session`. Um job em background autentica, lista, faz o diff, e o
resultado fica disponível via `AdoDiscoveryUseCase.status(sessionId)` (fase, terminado, e — quando
terminado — o diff em si). A UI reaproveita o componente de login device-code que o sync já tem
(mesmo texto, mesmo polling), só troca o que faz quando termina: em vez de contadores de eventos,
mostra a lista de repositórios a inserir/remover.

*Por que não uma chamada síncrona simples*: listar repositórios é rápido, mas a detecção de stage
por repo (decisão 3) inspeciona builds recentes um a um — pode levar alguns segundos por repo em
projetos grandes. Reaproveitar o padrão assíncrono já testado evita bloquear a requisição HTTP e
mantém a UI consistente com o único outro fluxo do produto que já pede login interativo.

### 2. Diff é por chave normalizada, escopado ao (organização, projeto) informado

O diff compara os nomes de repositório que o ADO devolve para o projeto com
`Repository.key()`/`organization()`/`project()` dos repositórios **já cadastrados para esse mesmo
par** — outros (organização, projeto) já cadastrados não entram na conta. Comparação
case-insensitive, mesma convenção já usada na atribuição de deploy por repo
(`repoToTeam.put(r.key().toLowerCase(...), ...)` em `StructureIndex`), porque o ADO devolve nomes de
repo em casing que nem sempre bate com o que foi digitado manualmente antes.

- **A inserir**: existe no ADO, não existe no cadastro desse par.
- **A remover**: existe no cadastro desse par, não existe mais no ADO.

### 3. Detecção de stage de produção: heurística sobre builds recentes, nunca adivinhação teimosa

Para cada repositório **a inserir**, o discovery busca os builds mais recentes desse projeto cujo
`repository.name` bate com o repo (mesmo filtro por nome que `fetchDeploys` já faz), lê o Timeline
de cada um, e coleciona os nomes de stage distintos que passam em
`AdoMapper.matchesProduction(stage, "")` (a heurística de fallback já existente — "prod"/"prd" como
substring, sem hardcode de template). Se sobrar **exatamente um** nome distinto, essa é a
`productionStage` sugerida; se sobrar zero ou mais de um, fica `null` — a mesma regra "sem dado
confiável, não inventa" que já rege o resto do motor (coverage/"no data" em vez de zero falso).

*Por que não perguntar ao admin pra cada repo durante o diff*: o objetivo é reduzir trabalho manual
no caso comum (nome de stage óbvio), sem transformar a tela de diff numa segunda rodada de
formulário por repositório — quando ambíguo, o campo fica vazio e o admin edita depois pela mesma
tela que já existe pra isso hoje.

### 4. Aplicar o diff reusa as operações que `RepositoryUseCase` já expõe

`register(...)` para cada inserção (com `teamId=null`, `productionStage` = o detectado ou `null`) e
`delete(...)` para cada remoção — nenhuma lógica de persistência nova; o discovery só decide *o
quê* aplicar, a aplicação em si é o CRUD existente. Isso preserva o invariante "1 repositório → 1
time" e todo o resto do comportamento já coberto por `RepositoryService`.

### 5. Novo outbound port dedicado, não sobrecarga de `AdoEventSourcePort`

`AdoEventSourcePort.fetchSince` tem uma forma específica (token + watermark → eventos). Descoberta
de repositório é uma operação diferente (token + org + projeto → repositórios + stage sugerido),
sem relação com o watermark incremental. Um port novo,
`AdoRepositoryDiscoveryPort.discover(token, organization, project)`, mantém cada porta com uma
responsabilidade só — o mesmo padrão já usado pra separar `AdoAuthPort` de `AdoEventSourcePort`.

## Risks / Trade-offs

- **Detecção de stage pode escolher errado se o projeto tiver mais de um pipeline com nomes de
  stage inconsistentes entre si** → mitigado por só preencher quando há exatamente um candidato
  distinto; ambiguidade vira campo vazio, nunca um palpite errado silencioso.
- **Projeto grande (muitos repositórios) torna a detecção de stage lenta** (uma chamada de builds +
  timeline por repositório novo) → aceito como trade-off do MVP; o job roda em background e a UI já
  tem o padrão de polling pronto para isso.
- **Diff pode incluir repositórios legitimamente arquivados/renomeados no ADO como "a remover"**,
  perdendo a associação de time se o admin aplicar sem olhar → mitigado por exigir revisão e
  confirmação explícita antes de aplicar (não-objetivo: aplicar sozinho).
- **Nomes de repositório colidindo só por case** entre dois cadastros diferentes do mesmo par
  (organização, projeto) não deveriam existir hoje (chave já é única), então o diff case-insensitive
  não introduz um risco novo de colisão.

## Migration Plan

Recurso aditivo — nenhuma migration de schema (usa a tabela `repository` já existente, nenhuma
coluna nova) e nenhuma mudança em endpoints existentes. Deploy = subir o binário novo; rollback =
reverter, o cadastro manual (que a descoberta usa por baixo) continua funcionando igual.

## Open Questions

- Se o diff deve permitir aplicar só uma parte (selecionar quais inserções/remoções aceitar) ou é
  tudo-ou-nada num único "Aplicar" — assumido tudo-ou-nada nesta change, pela simplicidade; seleção
  parcial fica pra depois se o uso real pedir.
