## Context

`AdoMapper.workItem()` mapeia hoje o `detail.hours` a partir de
`Microsoft.VSTS.Scheduling.CompletedWork`. Esse campo é opcional no Azure Boards e, em produção, está
vazio em ~93% dos itens (só 4 de ~20 contribuidores têm alguma hora, e só em `Task`). Consequência:
o **WIP** (Fluxo) e a **distribuição por tipo** (painel individual) — que somam esse `hours` —
ficam quase vazios apesar de haver atividade real.

O ADO expõe, por item, o histórico completo de alterações em
`GET .../_apis/wit/workitems/{id}/updates?api-version=7.1`: cada entrada traz o valor antes/depois
de cada campo alterado com um `revisedDate`. Filtrando as transições de `System.State` dá para
reconstruir a **duração em cada estado** ("time in status"), sem depender de preenchimento manual.

Restrições do harness: arquitetura hexagonal (mapeamento no `adapter-out-ado`; `domain`/`application`
sem Spring, guardado por ArchUnit), sync incremental idempotente por watermark, gates de qualidade
no `./gradlew build`.

## Goals / Non-Goals

**Goals:**
- WIP e distribuição por tipo passam a refletir atividade real via time-in-state derivado.
- Classificação de estado robusta a templates de processo (Basic/Agile/Scrum/CMMI) e customização.
- Custo controlado: só o delta incremental, reusando o watermark existente.
- "Sem dado" nunca vira zero silencioso — entra na cobertura.

**Non-Goals:**
- Tela de configuração de estados por template (fica heurística).
- Mudar DORA ou as demais métricas de Fluxo/IA.
- Backfill do backlog inteiro; mudança de schema de persistência.
- Ligar `pr_size`/`flow_efficiency` (esqueleto separado).

## Decisions

**D1 — Fonte da medida: histórico de `updates`, não `CompletedWork`.**
Reconstrói o time-in-state das transições de `System.State`. Alternativa (manter CompletedWork) foi
descartada: o dado não existe. Alternativa (usar `System.ChangedDate` só do estado atual) não dá
duração por estado.

**D2 — Substituir a medida nas métricas existentes (não criar métrica nova).** WIP e a distribuição
por tipo passam a ler a **duração em estados de andamento**; as chaves de métrica, endpoints e telas
não mudam. `CompletedWork`/`hours` é aposentado nessas duas métricas. Alternativa (evento/métrica
novo convivendo) traria mais superfície (catálogo + UI) sem resolver o valor vazio das atuais.

**D3 — Classificar estado por categoria, com fallback por nome.** As transições trazem **nomes** de
estado, não categorias. Buscamos a metadata de estados por tipo de item
(`.../_apis/wit/workitemtypes/{type}/states`, **cacheado por (projeto, tipo)** — poucas chamadas, não
por item) para obter a **categoria** (`Proposed`/`InProgress`/`Resolved`/`Completed`/`Removed`); os
`InProgress` contam como WIP. Quando a categoria falta, cai para heurística de nome
(`progress`/`active`/`doing`/`review`/`testing` → andamento; `done`/`closed`/`resolved`/`completed`/
`removed` → terminal). Nada hardcoded a um template único.

**D4 — Medida no `RawEvent` WORKITEM = duração em andamento; dois canais; "sem dado" explícito.**
Descoberto na implementação: o motor tem **dois canais de medida distintos** — `wip` lê o
`numericValue` do evento (measure `value`), enquanto a distribuição por tipo lê o `detail.hours`. Hoje
o `AdoMapper.workItem()` seta `numericValue = null` (por isso o WIP já vem 0 do ADO, **independente**
do CompletedWork) e `detail.hours` do CompletedWork (vazio). Correção: `workItem()` calcula a duração
em andamento `d` e a grava **nos dois canais** (`numericValue = d` e `detail.hours = d`), id estável
`wi:{id}`, mantendo `detail.type`. Itens **sem transição utilizável** ficam com medida **ausente**
(`numericValue = null`, sem `hours`) → continuam **vistos** mas **fora do valor** e **na cobertura
como não cobertos** — nunca zero.

**D4b — WIP = mediana do tempo em andamento.** O motor não tem agregação "soma de medida"; suas
agregações são SUM (conta eventos), MEDIAN, RATIO, SNAPSHOT, DISTINCT_RATIO. O `wip` passa de
`SNAPSHOT`/"itens" para **`MEDIAN`/"h"** (measure `value` = `numericValue` = duração em andamento),
menor-é-melhor: "tempo típico que um item passa em andamento". A distribuição por tipo continua
somando `detail.hours` por tipo.

**D5 — Só o delta incremental, uma chamada por item.** Reusa o conjunto já calculado por
`collectChangedWorkItemIds` (itens com `System.ChangedDate >=` watermark, com bissecção adaptativa);
para cada um, uma chamada a `updates`. A API de updates não tem batch — o recorte incremental é a
mitigação. Observação: `updates` devolve o **histórico completo** do item, então o watermark só
seleciona *quais* itens buscar; a completude de cada histórico é preservada (o caso "sem dado" real é
o item que nunca transicionou de estado).

## Risks / Trade-offs

- **Custo de N chamadas HTTP (uma por work item do delta)** → recorte incremental por watermark;
  metadata de estados cacheada por (projeto, tipo); idempotência mantém re-syncs baratos.
- **Nomes/categorias de estado divergentes por template/customização** → categoria do ADO primeiro,
  fallback por nome; nenhum estado hardcoded.
- **Interpretação de "andamento"** (ex.: incluir ou não `Resolved`) pode distorcer WIP → padrão:
  só `InProgress` conta como WIP; `Resolved/Completed/Removed` são terminais.
- **Reprocessamento**: re-sync recomputa a duração do histórico completo e sobrescreve `wi:{id}`
  (upsert idempotente) — sem duplicar nem divergir.
- **Zero vs. sem-dado**: se o marcador de "sem dado" não for respeitado pelo motor, o valor volta a
  parecer "sem trabalho" → o requisito e os testes fixam a distinção na cobertura.

## Migration Plan

- Sem mudança de schema: reusa `RawEvent`/`raw_event`. O significado de `numericValue` do WORKITEM
  muda (duração em andamento) e `CompletedWork` deixa de ser lido nessas métricas.
- Deploy: na próxima sincronização, os itens do delta são reprocessados com a nova medida
  (sobrescrevendo `wi:{id}`). Itens antigos entram conforme forem tocados (mesmo recorte incremental).
- Rollback: reverter o código; a próxima sync repopula os WORKITEM no formato anterior.

## Open Questions

- `Resolved` deve contar como andamento em algum template do time? Padrão assumido: não. Ajustável
  na heurística sem mudança de contrato se surgir necessidade real na aceitação.
