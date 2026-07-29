## Why

O motor lê o esforço de um work item do campo `Microsoft.VSTS.Scheduling.CompletedWork`, mas em
produção ele está **vazio em ~93% dos itens** (o time não usa esse campo no Azure Boards). Com isso,
duas métricas do PRD (`docs/initial-spec.md`) — **WIP** (Fluxo) e a **distribuição de trabalho por
tipo** (painel individual) — ficam praticamente vazias mesmo havendo atividade real. O Azure DevOps
expõe, por work item, o histórico completo de alterações (`GET .../_apis/wit/workitems/{id}/updates`),
de onde dá para reconstruir **quanto tempo o item ficou em cada estado** ("time in status") sem
depender de ninguém preencher hora manualmente.

## What Changes

- A ingestão passa a buscar, **por work item tocado desde o watermark** (mesmo filtro incremental de
  hoje — nunca o backlog inteiro), o seu histórico de `updates`, e a reconstruir a duração em cada
  `System.State` a partir das transições (`revisedDate` + valor antes/depois).
- Classificação de estados por **heurística de categoria** (não hardcoded por template): usa a
  `System.State` category do ADO (`Proposed`/`InProgress`/`Resolved`/`Completed`/`Removed`) quando
  presente, com fallback por nome; os estados **`InProgress`** contam como "em andamento" (WIP).
- **BREAKING (semântica de métrica):** o WIP e a distribuição por tipo passam a medir **tempo
  derivado em estados de andamento**, no lugar das horas de `CompletedWork`. As chaves de métrica,
  os endpoints e as telas continuam iguais; o que muda é a **fonte e o significado** do valor. O
  `CompletedWork` deixa de alimentar essas métricas.
- Quando um work item **não tem transição de estado** utilizável (criado e fechado no mesmo instante,
  ou o watermark cortou o histórico), o resultado é **"sem dado"** e entra na **cobertura** como não
  coberto — nunca um zero silencioso que pareceria "sem trabalho".

## Capabilities

### New Capabilities

_(nenhuma — a mudança ajusta comportamento de capacidades existentes)_

### Modified Capabilities

- `ado-integration`: a ingestão de work items passa a buscar o histórico de `updates` de cada item
  do delta incremental e a mapear a **duração por estado** (time-in-state); item sem transição vira
  "sem dado" em vez de evento zerado.
- `flow-dashboard`: o **WIP** passa a ser o tempo acumulado nos estados de andamento (derivado das
  transições), e não as horas de `CompletedWork`; "sem dado" não é contado como zero.
- `individual-dashboard`: a **distribuição de trabalho por tipo** passa a usar o tempo derivado em
  andamento por tipo de item, e não as horas de `CompletedWork`; "sem dado" não é contado como zero.

## Non-goals

- Não introduz configuração de estados por template no Admin — a classificação é por heurística de
  categoria (decisão desta proposta); uma tela configurável fica para uma change futura se preciso.
- Não altera métricas DORA nem as demais métricas de Fluxo/IA (Cycle Time, Throughput, PR Review
  Time, PR Size, Flow Efficiency, métricas de IA) — só WIP e a distribuição por tipo.
- Não faz backfill do backlog inteiro: mantém o recorte incremental por `System.ChangedDate >=`
  watermark; itens antigos entram conforme forem tocados.
- Não busca histórico em lote (a API de `updates` não tem endpoint batch) — o custo de 1 chamada por
  work item do delta é aceito e mitigado pelo recorte incremental.

## Impact

- **Código (adapter-out-ado):** `AdoEventSource.fetchWorkItems` (buscar `updates` por item do delta);
  `AdoMapper` ganha o mapeamento de time-in-state a partir das transições de `System.State`.
- **Código (application):** o WIP em `MetricCatalog` e a `workTypes()` em `IndividualDashboardService`
  passam a consumir a nova medida; `hours`/`CompletedWork` aposentado nessas duas métricas.
- **Contrato de evento (`RawEvent`):** o `WORKITEM` carrega a duração em andamento (e o estado) em vez
  de `hours` de CompletedWork; a cobertura reflete "sem dado".
- **Custo de sync:** +1 chamada HTTP por work item do delta incremental (sem batch); watermark/
  idempotência preservados.
- **Fora de escopo:** persistência (schema) não muda estrutura; segue o `RawEvent` existente.
