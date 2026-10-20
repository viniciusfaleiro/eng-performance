## 1. A seleção alcança os use cases que faltam

- [x] 1.1 `IndividualDashboardUseCase`/`Service`: assinatura com `ContainerFilter` (default delegando para a atual), repassada às métricas de entrega.
- [x] 1.2 `ComparisonHeatmapUseCase`/`Service`: idem, repassada aos cards de cada linha e ao `impact` da coluna de IA.
- [x] 1.3 `AiDashboardUseCase`/`Service`: idem, para que a resposta venha da regra única e não da ausência do parâmetro.
- [x] 1.4 `MetricsQueryUseCase.entityShares` (drill-down de pessoas): idem.

## 2. A distribuição individual responde à seleção

- [x] 2.1 `WorkDistribution` passa a receber a seleção: ALL e LEAVES contam folha (como hoje), CONTAINERS conta os itens que agrupam trabalho.
- [x] 2.2 `containersExcluded` passa a contar o lado que ficou de fora conforme a seleção, e o texto da tela acompanha.
- [x] 2.3 Testes: distribuição com cada uma das três seleções sobre o mesmo par mãe/filha.

## 3. Web: o parâmetro nos controllers que faltam

- [x] 3.1 `ContainerQuery` em `IndividualDashboardController`, `ComparisonHeatmapController` e `AiDashboardController`, e no `/metrics/{key}/people`.
- [x] 3.2 Teste por endpoint: valor inválido é 400 em todos (hoje é 200 onde o parâmetro não é declarado).

## 4. Prova de que não volta a escapar

- [x] 4.1 Teste que percorre as leituras de work item comparando `all` × `leaves` e exige que o número mude onde a métrica é filtrável — a lacuna atual passou porque nenhum teste cruzava endpoint com seleção.
- [x] 4.2 Checagem ponta a ponta no app: Comparativo e painel individual mudam com "Folhas"; IA e DORA seguem dizendo que não respondem; nenhum número muda sem seleção.
- [x] 4.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
