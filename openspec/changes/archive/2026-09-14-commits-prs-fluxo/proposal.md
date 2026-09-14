## Why

Os painéis de Fluxo respondem "quantos itens entregamos e em quanto tempo" (Throughput e Cycle
Time), mas não mostram **quanto trabalho de código** passou por baixo desses números. Sem a
quantidade de commits e de PRs do período ao lado, um Throughput baixo é ambíguo: pode ser time
parado, pode ser time entregando poucos itens grandes, pode ser trabalho que não virou work item.
Esses dois números de volume já existem como eventos crus (`COMMIT` e `PR`) no motor — só não são
expostos como métrica.

## What Changes

- Duas métricas novas no catálogo do motor, agregando eventos que já são ingeridos hoje:
  - `commit_count` — contagem de commits do período (evento `COMMIT`, soma).
  - `pr_count` — contagem de PRs do período (evento `PR`, soma).
- Ambas aparecem como **cards normais de Fluxo** (valor, evolução com polaridade, cobertura,
  sparkline, clicáveis com drawer de evolução + lista de itens do cálculo), ao lado de Throughput e
  Cycle Time, em **todos os níveis de navegação**: visão geral, vertical e time.
- No **painel individual**, os dois números entram na mesma série de entrega já reusada hoje
  (`throughput`, `cycle_time`, `ai_share`), de modo que a pessoa também vê seu volume de commits e
  PRs do período.
- São métricas de **volume/contexto, sem tier e sem benchmark** — não classificam ninguém.
- **Fora de escopo por decisão de produto:** o heatmap comparativo **não** ganha colunas de commits
  e PRs. Ranking de volume de commits entre times é métrica gameável e contraria o princípio de
  medir para melhorar o sistema, não para vigiar. O heatmap segue com as colunas atuais.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `flow-dashboard`: o grupo Fluxo passa a incluir as métricas de volume `commit_count` e `pr_count`,
  servidas como cards do dashboard de Fluxo em todos os níveis, sem tier, e explicitamente fora do
  conjunto de colunas do heatmap comparativo.
- `individual-dashboard`: a série de entrega do painel individual passa a incluir a contagem de
  commits e de PRs do período, junto de throughput, cycle time e %-com-IA.

## Impact

- `core/application` — `MetricCatalog` (duas definições novas + lista/acessor de volume),
  `FlowDashboardService` (compor os cards de volume), `IndividualDashboardService` (série de
  entrega). `ComparisonHeatmapService` permanece intacto, lendo só `catalog.fluxo()`.
- `adapter-in-web` — a SPA (`static/index.html`): lista `FLUXO`, mapas `PROTO2METRIC` e
  `FLOW_API2PROTO`, e os tiles de entrega do painel individual.
- `docs/api/openapi.yaml` — as novas chaves de métrica nos exemplos/enum onde as chaves de Fluxo
  são documentadas.
- Sem migração de banco: os eventos `COMMIT`/`PR` já são gravados em `raw_event`; as métricas são
  agregadas on-read. Sem reprocessamento de ingestão.
- O drill-down de itens funciona de imediato: commits e PRs já carregam `summary` e `url` no
  `detail` do evento.
