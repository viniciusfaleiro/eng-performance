## Why

Hoje o app mistura duas linguagens visuais pro mesmo tipo de dado: métricas por período viram
linha (`lineChart`) em cinco lugares diferentes (hero do dashboard, drawer, Tendências, comparação
com/sem IA) e mini-linha (`sparkline`) no card — enquanto o resto da tela (ranking, fases de Cycle
Time, comparativo) já usa barra. Período semanal/mensal/diário não é uma amostra contínua, é um
balde discreto — barra é a linguagem visual mais consistente com o resto do produto e com o que o
dado realmente é.

## What Changes

- **CHANGED** — todo gráfico de série temporal (`lineChart`) passa a renderizar como **barras
  verticais** com eixo Y ancorado em zero, nos 5 usos existentes: hero do dashboard (DORA/Fluxo/IA),
  drawer de métrica, Tendências (série única e a comparação com/sem IA), e a comparação de Cycle
  Time com IA vs. sem IA no dashboard de IA.
- **CHANGED** — a comparação de 2 séries sobrepostas (com IA vs. sem IA) vira **barras agrupadas**
  (duas barras lado a lado por período), preservando a legenda por cor já existente.
- **CHANGED** — o `sparkline` dentro do card de métrica (mini-tendência de 32px) também vira barra.
- **NEW** — como o eixo zerado achata a variação visual de métricas que oscilam numa faixa estreita
  e alta (cycle_time, lead_time, pr_review_time, mttr, pr_size), o `%` de evolução por período
  (já existente nos tooltips e nas tiles) ganha destaque visual maior perto do gráfico, pra
  compensar a perda de leitura que o zoom da linha antiga dava de graça.
- O último período (parcial, quando o balde atual ainda não fechou) continua sinalizado
  visualmente — antes era um ponto na linha, agora é um estilo diferente na última barra
  (contorno tracejado ou opacidade reduzida).
- Tooltip por período (hoje já existe via `<title>` nos hit-boxes da linha) é preservado igual.

## Non-goals

- **Não** muda o scatter (throughput × cycle time), o donut de IA, o `comparativo()` (já é barra
  horizontal) nem o `phaseBlock` de Cycle Time (já é barra empilhada) — nenhum deles é linha hoje.
- **Não** muda nenhum valor, cálculo ou definição de métrica — é troca de renderização, os dados
  que alimentam o gráfico continuam exatamente os mesmos (`series`, `snap`, `trendPts` etc.).
- **Não** adiciona biblioteca de gráficos nova — a implementação atual é SVG desenhado à mão
  (sem dependência externa); a troca mantém esse padrão.
- **Não** mexe em nenhum endpoint de API nem em `docs/api/openapi.yaml` — é uma mudança
  inteiramente client-side, o backend já entrega os mesmos pontos de série.

## Capabilities

Nenhuma spec existente normatiza o **tipo** de gráfico (o requisito de `metrics-navigation` fala em
"a série é exibida ao longo do tempo", sem especificar linha ou barra) — a escolha visual em si é
detalhe de implementação. Mas as decisões desta change (eixo ancorado em zero, tooltip por período,
sinalização do período parcial, barras agrupadas na comparação com/sem IA) são comportamento
testável da UI que vale documentar como contrato, não só como código.

### New Capabilities
- `metric-charting`: como uma série de métrica (1 ou 2 séries) é renderizada em barras — eixo
  zerado, tooltip por período, sinalização visual do período parcial, barras agrupadas quando há
  duas séries.

### Modified Capabilities
<!-- nenhuma -->

## Impact

- **UI** (`adapters/in-web/src/main/resources/static/index.html`): reescreve `lineChart(...)` para
  `barChart(...)` (ou equivalente), ajusta `sparkline(...)`, e os 6 pontos de chamada continuam
  passando os mesmos dados (`seriesArr`, `labels`, `unit`, `height`).
- Nenhum outro módulo (`domain`, `application`, `adapter-out-*`) é afetado — a mudança é
  inteiramente dentro do `adapter-in-web`, na SPA servida.
