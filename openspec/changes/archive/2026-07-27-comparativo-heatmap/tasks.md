## 1. Aplicação — reuso do ai_impact (module `application`, sem Spring)

- [x] 1.1 Extrair a composição do `ai_impact` para um método público `AiDashboardUseCase.impact(nodeId, freq)` que devolve o `AiCard` (valor = quão mais rápido com IA, cobertura = share de PRs com IA); `AiDashboardService.dashboard` passa a reusar esse método
- [x] 1.2 Teste: `impact` isolado bate com o card `ai_impact` do dashboard para o mesmo nó (coorte vazia → sem comparação)

## 2. Aplicação — heatmap composto

- [x] 2.1 `ComparisonHeatmap`/`HeatmapRow`/`HeatmapMetric` (records) + porta inbound `ComparisonHeatmapUseCase` (parâmetros nó, freq, escopo verticais|times no overview)
- [x] 2.2 Constante da ordem das colunas do heatmap = catálogo DORA + Fluxo + `ai_share`/`ai_adoption`/`ai_impact`, com key+label+unidade de cada métrica
- [x] 2.3 `ComparisonHeatmapService`: escolhe as linhas (filhos do nó — all→times|verticais, vertical→times, time→pessoas, pessoa→colegas); para cada linha lê os 12 valores de `metrics.cards(linha)` na ordem das colunas + anexa `ai_impact` via `impact(linha)`; sem cor/ranking (presentacional no cliente)
- [x] 2.4 Testes de aplicação (fakes): matriz filhos×métricas na ordem certa; valor de uma célula == card do mesmo nó; linhas node-aware (overview times/verticais, vertical→times, time→pessoas)

## 3. Adapter web (module `adapter-in-web`)

- [x] 3.1 `GET /api/comparison/heatmap?node=&freq=&scope=` + DTOs (metrics[{key,label,unit}] + rows[{nodeId,label,rowType,values[]}])
- [x] 3.2 Enforcement: 403 se `canView(node)` falha; linhas de estrutura filtradas por `canView`; linhas de pessoa só com `canViewIndividual` (admin ou conta gestora/própria)
- [x] 3.3 Testes de web (MockMvc): heatmap em escopo (200) com linhas+colunas dos 3 grupos; nó fora de escopo (403); conta org-wide num time → sem linhas de pessoa; gestor do time → recebe as pessoas

## 4. Frontend & composição (modules `adapter-in-web`, `bootstrap`)

- [x] 4.1 Wiring do `ComparisonHeatmapUseCase`
- [x] 4.2 Frontend: view Comparativo sai do sintético e lê `/api/comparison/heatmap` — mesma tabela heat (grupos DORA/Fluxo/IA, toggle Todos os times/Todas as verticais em all, cor relativa por coluna calculada no cliente, legenda). Normalização de unidades igual aos dashboards (deploy_freq/dia, razões em %)

## 5. Fronteiras, paridade e fechamento

- [x] 5.1 ArchUnit verde (domínio e application sem Spring; JPA só no adapter-out)
- [x] 5.2 Loop de paridade visual (logado como admin): **chrome** do Comparativo (topbar, árvore, breadcrumb, toggle de escopo, tabela heat com grupos DORA/Fluxo/IA, células, legenda) bate **0px** com o protótipo. Os **números** refletem o motor — divergem do mock por serem reais (esperado; regra S3-S6). Verificar também um nó de Time como gestor (linhas de pessoa) vs. exec (sem pessoas)
- [x] 5.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo 70%/60% em domain+application, ArchUnit); DORA/Fluxo/IA/S1-S6 intactos
