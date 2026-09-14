## 1. Catálogo de métricas

- [x] 1.1 Adicionar a definição `commit_count` ("Commits", `COMMIT`, `SUM`, unidade "commits", `HIGHER_BETTER`, sem `TierBands`) em `MetricCatalog`
- [x] 1.2 Adicionar a definição `pr_count` ("Pull Requests", `PR`, `SUM`, unidade "PRs", `HIGHER_BETTER`, sem `TierBands`) em `MetricCatalog`, com comentário registrando por que é distinta de `code_throughput`
- [x] 1.3 Adicionar a lista `VOLUME` e o acessor `volume()` em `MetricCatalog`, mantendo `FLUXO` intacta
- [x] 1.4 Testar em `MetricCatalog`/engine que as duas métricas contam todos os eventos do tipo no período (sem filtro de população) e que não têm tier

## 2. Dashboard de Fluxo (application)

- [x] 2.1 Compor os cards de volume em `FlowDashboardService`, após os cards de `catalog.fluxo()`
- [x] 2.2 Testar em `FlowDashboardServiceTest` que Commits e Pull Requests vêm com valor, evolução e cobertura
- [x] 2.3 Testar que os cards de volume aparecem nos três níveis (visão geral, vertical e time)
- [x] 2.4 Testar em `ComparisonHeatmapServiceTest` que o heatmap **não** ganhou colunas de `commit_count`/`pr_count`

## 3. Painel individual (application)

- [x] 3.1 Incluir `commit_count` e `pr_count` na série de entrega de `IndividualDashboardService.delivery(...)`
- [x] 3.2 Testar em `IndividualDashboardServiceTest` que as cinco séries de entrega são devolvidas para a pessoa

## 4. SPA — cards e drawer

- [x] 4.1 Acrescentar `cmt` (Commits) e `prc` (Pull Requests) à lista `FLUXO` do `static/index.html`, depois dos drill-downs de PR
- [x] 4.2 Mapear as duas chaves em `PROTO2METRIC` e `FLOW_API2PROTO` para que os cards leiam dados reais do motor
- [x] 4.3 Verificar que o drawer abre nos dois cards com gráfico de evolução vindo da série real e com a lista "Itens considerados no cálculo" (o mapeamento em `PROTO2METRIC` é o que liga o drill-down)
- [x] 4.4 Conferir no drawer que cada item listado mostra descrição legível e link para o commit/PR no Azure DevOps

## 5. SPA — painel individual

- [x] 5.1 Exibir os tiles de Commits e Pull Requests no painel individual, ao lado de Throughput e Cycle Time, lendo as novas séries de entrega
- [x] 5.2 Conferir que os tiles do individual seguem o mesmo padrão visual dos existentes (valor, unidade, chip de evolução)

## 6. Documentação e fechamento

- [x] 6.1 Documentar as chaves `commit_count` e `pr_count` em `docs/api/openapi.yaml` onde as chaves de métrica de Fluxo já são descritas
- [x] 6.2 Validar ponta a ponta com a app rodando: cards nos três níveis, drawer com gráfico e itens, e painel individual
- [x] 6.3 Rodar `./gradlew spotlessApply` e `./gradlew build` até `BUILD SUCCESSFUL`
