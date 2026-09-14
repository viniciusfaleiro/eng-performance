## Why

Os números de dashboard (DORA, Fluxo, IA) hoje se explicam só pela definição da métrica — o drawer
mostra o valor, a evolução e o gráfico, nunca **quais eventos** entraram na conta. Quando um número
não bate com o que o time enxerga no Azure DevOps, não há como confirmar: é uma diferença de
convenção de estado, um item mal atribuído, um repo sem time, ou de fato um bug no motor? Hoje a
única forma de investigar é ler código ou consultar o banco direto. O troubleshooting precisa
acontecer em tela, com link de volta pro item real no ADO.

## What Changes

- **NEW** — o drawer de métrica ganha uma seção "Itens considerados no cálculo": lista dos eventos
  crus que alimentaram o valor exibido no período atual, cada um com link para o item no Azure
  DevOps, um rótulo (título do PR/work item, resumo do commit), a entidade a que foi atribuído
  (pessoa ou repo/time) e o valor/medida que esse item contribuiu para a conta (ex.: horas de
  cycle time, 1/0 de CFR, linhas do PR).
- **NEW** — a lista reflete exatamente a agregação da métrica: para `SUM`/`RATIO`/`DISTINCT_RATIO`,
  todo evento correspondente ao nó no período; para `MEDIAN`, só os que carregam a medida (os sem
  dado aparecem sinalizados como excluídos); para `SNAPSHOT` (WIP), só o evento mais recente de
  cada entidade no período — o mesmo subconjunto que o motor de fato soma.
- **NEW** — endpoint `GET /api/metrics/{key}/items` (node, freq, e o `bucket` do ponto selecionado
  no gráfico — por padrão o período atual) devolvendo essa lista, com o mesmo enforcement de RBAC
  dos demais endpoints de métrica (403 fora do escopo).
- **CHANGED** — o adapter do Azure DevOps passa a capturar título e link também para **work item**
  (`System.Title` + `{org}/{project}/_workitems/edit/{id}`), **deploy** (link do pipeline run) e
  **review** (link da PR de origem) — hoje só PR e commit têm `url`/`summary` no `detail` do
  evento. Sem isso, justamente Cycle Time, Throughput, WIP, Flow Efficiency, `deploy_freq`,
  `lead_time`, `cfr` e `mttr` apareceriam na lista sem link nem título.
- A lista cobre apenas os itens **atribuídos** ao nó (o mesmo escopo que já alimenta o número) —
  o balde de "não atribuídos" da cobertura não entra nesta tela.

## Capabilities

### New Capabilities
- `metric-drilldown`: lista, por métrica/nó/frequência/período, os eventos crus considerados no
  cálculo, com link ADO, rótulo, entidade atribuída e a medida usada — a ferramenta de
  troubleshooting entre o número exibido e a realidade no Azure DevOps.

### Modified Capabilities
- `ado-integration`: a ingestão de work item, deploy e review passa a capturar título/link
  (`url`) no `detail` do evento, no mesmo padrão já usado por PR e commit.

## Non-goals

- **Não** cobre eventos não atribuídos (identidade sem Pessoa, repo sem time) — essa é a métrica
  de cobertura já existente nos cards, não o escopo desta change.
- **Não** adiciona filtro/paginação avançada na lista — o volume por bucket (um período de
  daily/weekly/monthly, já recortado por nó) é pequeno o bastante para caber sem paginação; se um
  nó de alto volume (ex. "Visão geral", mensal) provar o contrário em uso real, isso vira uma
  change própria.
- **Não** muda nenhum valor de métrica, agregação ou definição do catálogo — é puramente uma
  janela de leitura sobre o que o motor já calcula, sem alterar o resultado exibido nos cards.
- **Não** adiciona exportação (CSV/planilha) da lista nesta change.
- **Não** estende a lista para o histórico completo do nó — só o período (`bucket`) selecionado.

## Impact

- **Specs**: nova `openspec/specs/metric-drilldown`; delta em `openspec/specs/ado-integration`.
- **domain**: sem mudança — `RawEvent`/`MetricDefinition`/`Aggregation` já carregam o necessário.
- **application**: `MetricsEngine` ganha uma operação de itens (reaproveitando a lógica hoje
  privada em `match()`/`aggregate()`, sem duplicar regra de negócio); `MetricsQueryUseCase` ganha
  `items(key, node, freq, bucket)`; novo tipo de retorno inspirado em `ActivityItem` (id, tipo,
  link, rótulo, data, entidade, medida, se contou ou foi excluído).
- **adapter-out-ado**: `AdoMapper.workItem/deploy/reviews` passam a preencher `url`/`summary` no
  `detail`; `AdoEventSource` precisa repassar org/project para `workItem()` montar o link.
- **adapter-in-web**: `MetricsController` ganha `GET /api/metrics/{key}/items`; novo DTO;
  `docs/api/openapi.yaml` atualizado.
- **UI** (`static/index.html`): `openDrawer` ganha a seção de itens — tabela com link externo,
  rótulo, entidade e medida, carregada sob demanda ao abrir o drawer.
