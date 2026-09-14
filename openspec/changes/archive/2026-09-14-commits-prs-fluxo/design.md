## Context

O motor de métricas já agrega on-read por dimensão × frequência × estatística sobre a tabela
`raw_event`. Eventos `COMMIT` e `PR` já são ingeridos pelo adapter do Azure DevOps e já carregam
`summary` e `url` no `detail` (usados pelo drill-down de itens). O que falta é apenas **expor
contagem desses eventos como métrica** — nenhuma mudança de ingestão, schema ou agregação.

Hoje o catálogo já tem uma métrica de contagem de PRs: `code_throughput` ("PRs concluídos"), mas
ela existe como **drill-down de código do dashboard de IA** (comparação das coortes com/sem IA),
não como card de Fluxo, e não está em `MetricCatalog.FLUXO`.

Restrição importante da composição atual: `ComparisonHeatmapService` monta as colunas com
`catalog.dora() + catalog.fluxo() + catalog.ia()`. Ou seja, **qualquer chave adicionada a
`MetricCatalog.FLUXO` aparece automaticamente no heatmap comparativo** — que é justamente onde a
decisão de produto diz para as métricas de volume **não** irem.

## Goals / Non-Goals

**Goals:**
- Expor `commit_count` e `pr_count` como métricas de primeira classe do motor.
- Exibi-las como cards normais de Fluxo (com drawer, evolução, cobertura e lista de itens) em todos
  os níveis: visão geral, vertical e time.
- Exibi-las no painel individual, junto da série de entrega já existente.
- Não regredir o heatmap comparativo nem o dashboard de IA.

**Non-Goals:**
- Não adicionar colunas de volume ao heatmap comparativo.
- Não atribuir tier/benchmark a essas métricas — são volume, não performance.
- Não mexer em ingestão, schema, `raw_event` ou reprocessamento.
- Não redefinir Throughput (segue sendo work item concluído, nunca PR).

## Decisions

### 1. Nova lista `VOLUME` no catálogo, separada de `FLUXO`

Adicionar `MetricCatalog.VOLUME = List.of("commit_count", "pr_count")` e um acessor `volume()`,
mantendo `FLUXO` intacta.

- **Por quê:** é o que permite atender as duas exigências ao mesmo tempo — cards no dashboard de
  Fluxo **sem** colunas novas no heatmap. `FlowDashboardService` passa a compor
  `catalog.fluxo() + catalog.volume()`; `ComparisonHeatmapService` continua lendo só
  `catalog.fluxo()` e não muda uma linha.
- **Alternativa descartada:** adicionar as chaves direto em `FLUXO` e filtrar no heatmap. Rejeitada
  porque inverte a responsabilidade: o heatmap passaria a precisar conhecer quais métricas de Fluxo
  ele deve esconder, e a próxima métrica de volume quebraria o heatmap por omissão. Com listas
  separadas, o default é seguro.

### 2. `pr_count` nova em vez de reusar `code_throughput`

Criar `pr_count` ("Pull Requests", `PR`, `SUM`, unidade "PRs") em vez de promover `code_throughput`
a card de Fluxo.

- **Por quê:** `code_throughput` tem papel semântico próprio — é o denominador das coortes com/sem
  IA no dashboard de IA, e seu label ("PRs concluídos") comunica outra coisa. Promovê-la a card de
  Fluxo acoplaria os dois dashboards: mudar o label ou a população de uma quebraria a outra.
- **Trade-off aceito:** duas definições com a mesma agregação sobre o mesmo tipo de evento. É
  duplicação de *configuração*, não de lógica — o motor é o mesmo — e compra independência entre os
  dashboards.

### 3. Sem população filtrada: conta todo evento do tipo no período

`commit_count` e `pr_count` não entram em `MetricCatalog.POPULATIONS` — contam todos os eventos
`COMMIT`/`PR` do bucket.

- **Por quê:** a pergunta que o card responde é "quanto código passou por aqui no período", não
  "quanto foi concluído". Throughput já é a métrica de conclusão, e é dela que essas duas se
  distinguem; filtrar por `completed` transformaria `pr_count` numa cópia do Throughput com outra
  unidade e destruiria o valor de contraste.

### 4. Direção `HIGHER_BETTER`, sem `TierBands`

- **Por quê:** o motor exige uma direção para calcular a polaridade da evolução (a setinha verde/
  vermelha). `HIGHER_BETTER` é a leitura menos enganosa para volume de atividade. A ausência de
  `TierBands` é o que impede a métrica de virar classificação — o card renderiza sem selo de tier,
  igual às demais métricas de Fluxo.

### 5. Painel individual reusa a série de entrega existente

`IndividualDashboardService.delivery(...)` hoje devolve as séries de `throughput`, `cycle_time` e
`ai_share`. Basta acrescentar `commit_count` e `pr_count` à lista.

- **Por quê:** o contrato (`List<MetricSeries> delivery`) já é uma lista, o front já itera por
  chave, e a regra de coaching-only do painel individual continua valendo sem nenhuma alteração de
  autorização.

## Risks / Trade-offs

- **Contagem de commits é métrica gameável e historicamente mal usada** → mitigada por três
  escolhas explícitas: sem tier/benchmark, fora do heatmap comparativo (nenhum ranking público
  entre times), e no individual apenas dentro do painel de coaching, que já é restrito ao próprio
  liderado/gestor/admin.
- **Leitura inflada por bots e merge commits** → a cobertura por identidade já sinaliza eventos não
  atribuídos, e a convenção de identidade própria para bots (já documentada no painel de
  convenções) é o controle existente. Não muda nesta change; só vale registrar que o número é
  bruto.
- **Mais dois cards no grid de Fluxo (7 → 9)** → o grid já é responsivo e os cards são uniformes;
  o custo é de densidade visual, não de layout quebrado. Ordem escolhida: volume por último, depois
  dos drill-downs de PR, para não competir com a leitura das métricas de entrega.
- **`pr_count` e `code_throughput` podem divergir no futuro** se alguém aplicar população a uma e
  não à outra → o comentário no catálogo registra a distinção de papéis, e os testes cobrem cada
  uma no seu dashboard.
