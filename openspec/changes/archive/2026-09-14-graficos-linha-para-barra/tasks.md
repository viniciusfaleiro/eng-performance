## 1. Gráfico principal (barra, eixo zerado, 2 séries)

- [x] 1.1 Criar `barChart(seriesArr, labels, unit, height)` reaproveitando a assinatura de `lineChart`: eixo Y sempre `mn=0`, `mx` com ~10% de folga acima do maior valor.
- [x] 1.2 Renderizar 1 barra por período quando `seriesArr.length===1`; 2 barras agrupadas lado a lado por período quando `seriesArr.length===2` (cor de cada série já vem do chamador).
- [x] 1.3 Manter o hit-box por período (largura total do balde, não só a barra) com `<title>` listando o valor de todas as séries daquele período — mesmo padrão dos hit-boxes atuais de `lineChart`.
- [x] 1.4 Estilizar a última barra como período parcial (borda tracejada / opacidade reduzida) no lugar do `<circle>` que hoje marca o ponto atual.
- [x] 1.5 Trocar os 4 call-sites (`areaTrend`, `openDrawer`, `viewTendencias`, comparação com/sem IA no dashboard de IA) de `lineChart(...)` para `barChart(...)` — sem mudar como cada um monta `seriesArr`/`labels`.
- [x] 1.6 Remover `lineChart` (ou deixar só se algo mais depender dela — conferir que nenhum outro ponto do arquivo ainda chama).

## 2. Sparkline do card

- [x] 2.1 Reescrever `sparkline(trend)` para renderizar barras minúsculas na mesma área (200×32), escala relativa ao próprio trend (não zero-ancorada, é decorativa).
- [x] 2.2 Destacar a última barra (cor mais forte) no lugar do `<circle>` que hoje marca o valor atual.

## 3. Evolução em destaque no hero do dashboard

- [x] 3.1 Estender `areaTrend(title, m)` para calcular `snap(state.node, m, state.freq)` e renderizar o `evoChip` (já existente) ao lado do título, no `chart-head` — mesmo padrão já usado em `viewTendencias`.

## 4. Fechamento

- [x] 4.1 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL** (a mudança é só na UI estática, mas o build ainda precisa passar verde).
- [x] 4.2 Subir o app localmente (`docker compose up -d db` + `METRICS_REFERENCE_DATE=2026-06-29 ./gradlew :bootstrap:bootRun`, dados de seed) e conferir visualmente: hero de cada dashboard (DORA/Fluxo/IA), drawer de métrica, Tendências (série única e com o toggle "Comparar com/sem IA"), sparkline dos cards, tema claro e escuro.
