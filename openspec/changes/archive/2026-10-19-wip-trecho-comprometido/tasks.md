## 1. Domínio — fila comprometida, filtro de contêiner, intervalo declarado

- [x] 1.1 Acrescentar `Segment.QUEUE` (ou equivalente) e varrer todo uso de `Segment` no repositório (`grep -rn "Segment\."`), garantindo que os `switch`/`if` existentes tratem o valor novo como espera — as horas de Espera/Ativo/Review de qualquer item têm de ficar idênticas.
- [x] 1.2 `MetricDefinition.over(Occupation.INTERVAL)` ganha a variante com a chave do `detail` a ler (default `spans`), e um acessor para o motor consultar.
- [x] 1.3 Promover a regra de folha de `WorkDistribution.isContainer` para o domínio (contêiner = está em `parentWorkItemIds()` **ou** é Epic por tipo), com teste próprio, e fazer `WorkDistribution` passar a usá-la.
- [x] 1.4 Criar `ContainerFilter` (ALL / CONTAINERS / LEAVES) no molde de `WorkTypeFilter`: `matches(RawEvent, Set<String> parentIds)`, `none()`/default ALL, e a noção de "não se aplica" para métrica que não é de work item.
- [x] 1.5 Testes de domínio: filtro nos três estados, composição com tipo de trabalho, Epic sem filhas ainda é contêiner, item com filhas fora do período ainda é contêiner.

## 2. Ingestão — classificação da fila e trecho comprometido

- [x] 2.1 `StateClassifier`: lista nova de palavras-chave da fila comprometida (`ready`, `pronto`), checada **antes** de `WAIT_HINTS`; tirar `ready` de `WAIT_HINTS`; `fromCategory` devolve QUEUE para `Proposed` que casa a lista nova. Testes por estado real do board (Refinement, Ready, Doing, Code Review, Em homolog, Pronto p/ Prod, New, Active, Done, Resolved, Closed).
- [x] 2.2 `WorkItemFlow`: acumular o **trecho comprometido** — da primeira entrada em QUEUE/ACTIVE/REVIEW até o terminal (ou `now`), contínuo —, subtrair os intervalos impedidos (`Intervals.subtract` com `ImpedimentTimeline.blocked()`) e gravar em `committed_spans`.
- [x] 2.3 Testes de `WorkItemFlow`: Refinement→Ready→Doing→Code Review→Pronto p/ Prod→Done (trecho começa no Ready, termina no Done); Task New→Active→Done (começa no Active); item que só ficou no backlog não grava nada; item impedido no meio tem o trecho recortado; item aberto deixa a ponta em aberto; **e as horas das três fases continuam idênticas às de antes em todos os casos**.
- [x] 2.4 Teste ponta a ponta no `AdoMapperTest` com fixture de `updates` cobrindo o fluxo completo do board real.

## 3. Motor — intervalo declarado e população filtrada

- [x] 3.1 `InProgressSpans.of(...)` passa a aceitar a chave do `detail`, com fallback explícito para `spans` quando a chave declarada não existe no evento (itens ingeridos antes desta change); documentar o fallback no javadoc.
- [x] 3.2 `MetricsEngine.match` passa a ler a chave declarada pela definição; `MetricCatalog` declara `committed_spans` no WIP.
- [x] 3.3 `MetricsEngine.restrict` compõe o `ContainerFilter` com o filtro de tipo de trabalho e a coorte; `MetricsService` resolve `parentWorkItemIds()` uma vez por leitura e injeta.
- [x] 3.4 Testes de motor: pertinência pelo trecho comprometido (item em fila de deploy conta; item impedido o período inteiro não conta); fallback para `spans` em evento antigo; filtro de contêiner nos três estados; duas métricas de intervalo lendo chaves diferentes no mesmo evento.

## 4. Visibilidade do WIP (só no diário)

- [x] 4.1 `FlowDashboardService` omite o card de WIP quando a frequência do período não é diária.
- [x] 4.2 `ComparisonHeatmapService` (ou equivalente) omite a coluna de WIP fora do diário, no mesmo lugar onde as métricas de volume já são excluídas.
- [x] 4.3 SPA: WIP sai da lista de métricas de Tendências fora do diário, e a seleção corrente cai para outra métrica se o usuário trocar a frequência com o WIP selecionado (não pode ficar numa tela vazia).
- [x] 4.4 Testes: dashboard diário tem o card, semanal/mensal não; heatmap idem; o payload não traz a métrica fora do diário.

## 5. Filtro de contêiner na API e na tela

- [x] 5.1 Parâmetro de query no molde de `WorkTypeQuery` (400 para valor desconhecido), aplicado nos controllers que já recebem o filtro de tipo de trabalho (métricas, Fluxo, IA, individual, heatmap).
- [x] 5.2 SPA: chip de três estados ao lado do filtro de Tipo de trabalho, refletindo a seleção, viajando na URL (`syncUrl`/`readUrl`) como `workType` já viaja.
- [x] 5.3 Eco na tela de quando a seleção não se aplica à métrica aberta, no mesmo padrão do filtro de tipo de trabalho.
- [x] 5.4 Testes web: query inválida é 400; a seleção chega ao serviço; a URL reabre a mesma leitura.

## 6. Explicações (botões "i")

- [x] 6.1 Reescrever a explicação do WIP: trecho comprometido (fila inclusa), desconto do impedimento, contêiner contando por padrão e como filtrar, por que só existe no diário, e que períodos ingeridos antes da regra foram medidos de outro jeito até reprocessar.
- [x] 6.2 Acrescentar à explicação do Cycle Time a nota de que ele começa no primeiro estado de trabalho, enquanto o WIP começa no compromisso — os dois cobrem trechos diferentes de propósito.
- [x] 6.3 Explicação do filtro de contêiner (o que é contêiner, por que a escolha existe, e que o default conta os dois), pendurada no chip novo.
- [x] 6.4 `MetricCatalogExplanationTest` verde com as chaves novas.

## 7. Fechamento

- [x] 7.1 Paridade visual: rodar o app, conferir por screenshot o chip novo e a ausência do WIP fora do diário, comparando com o design system do protótipo.
- [ ] 7.2 Checagem ponta a ponta: sincronizar um item que passe por Ready → Doing → Pronto p/ Prod → Done com um trecho impedido; conferir que ele entra no WIP do dia em que estava em Pronto p/ Prod, não entra nos dias impedidos, some ao trocar para Semanal, e muda com o filtro de contêiner.
- [x] 7.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
