## 1. Motor de métricas (application, sem mudar domain)

- [x] 1.1 Estender o record privado `Matched` em `MetricsEngine` com o campo `RawEvent source`, propagado por `match()` — sem mudar o comportamento de `series`/`card`/`coverage`/`aggregate`.
- [x] 1.2 Criar `com.engperf.application.metrics.MetricDrilldownItem` (record: `eventId`, `eventType`, `url`, `label`, `entity`, `occurredAt`, `measure`, `unit`, `counted`, `excludedReason`), no padrão de `ActivityItem`.
- [x] 1.3 Implementar `MetricsEngine.items(index, events, def, nodeId, freq, reference, bucketStart, population)`: reaproveita `match()`, filtra pelo `Bucket` de `bucketStart` (mesmo `Frequency.lastBuckets` da série), e seleciona `counted`/`excludedReason` espelhando `aggregate()` por `Aggregation` (SUM/RATIO/DISTINCT_RATIO = todos contam; MEDIAN = sem medida não conta; SNAPSHOT = só o mais recente por entidade conta).
- [x] 1.4 Testes unitários de `MetricsEngine.items(...)` cobrindo as 5 agregações: SUM lista tudo, MEDIAN sinaliza os sem medida, RATIO lista tudo com seu num/den, SNAPSHOT sinaliza os superados pela mesma entidade, DISTINCT_RATIO lista tudo com o flag de quem contou.

## 2. Porta e serviço

- [x] 2.1 Estender `MetricsQueryUseCase` com `items(String key, String node, Frequency freq, String bucketStart)` (bucketStart nulo = último bucket).
- [x] 2.2 Implementar em `MetricsService`, resolvendo `bucketStart` para a `LocalDate` de referência via os mesmos buckets que `series(...)` já usa; erro claro (`NoSuchElementException`) para `key` inexistente na catálogo, como os demais métodos do serviço.
- [x] 2.3 Teste unitário de `MetricsService.items(...)`: bucket omitido cai no último período; bucket explícito bate com o ponto correspondente da série.

## 3. Adapter Azure DevOps (título + link em work item, deploy, review)

- [x] 3.1 `AdoMapper.workItem(...)` ganha os parâmetros `org`/`project`; `detail.put("summary", título)` a partir de `System.Title` e `detail.put("url", org + "/" + enc(project) + "/_workitems/edit/" + id)`.
- [x] 3.2 `AdoEventSource.fetchWorkItems` passa `System.Title` no `fields` da chamada batch existente (sem round-trip extra) e repassa `org`/`proj` para `AdoMapper.workItem(...)`.
- [x] 3.3 `AdoMapper.deploy(...)` ganha `detail.put("url", webLink(run))`, reaproveitando o helper `webLink` já usado para PR.
- [x] 3.4 `AdoMapper.reviews(JsonNode pr)` ganha `detail.put("url", webLink(pr))` (a review linka para a PR de origem).
- [x] 3.5 Testes de `AdoMapper` (unitários, com fixtures de payload) cobrindo título/link de work item, deploy e review; ajustar os testes existentes de `AdoEventSourceTest` que constroem esses payloads/chamadas, se necessário.

## 4. Web (endpoint + DTO)

- [x] 4.1 Criar `MetricsDtos.DrilldownItemDto` (mapeando `MetricDrilldownItem`) e `GET /api/metrics/{key}/items` em `MetricsController`, com `node`/`freq`/`bucket` (todos opcionais exceto `key`) e o mesmo `requireView` (403 fora do escopo) dos demais endpoints.
- [x] 4.2 Teste de web (MockMvc): 200 com a lista para um nó dentro do escopo, 403 para nó fora do escopo, bucket omitido devolve o período atual.
- [x] 4.3 Atualizar `docs/api/openapi.yaml` com o endpoint novo e o schema `DrilldownItem`.

## 5. UI (drawer)

- [x] 5.1 Adicionar ao `openDrawer` uma seção "Itens considerados no cálculo", carregada sob demanda (um `fetch`/`jget` a `/api/metrics/{key}/items` ao abrir o drawer, com estado de carregando).
- [x] 5.2 Renderizar cada item: link externo (quando houver `url`), rótulo, entidade atribuída, medida formatada na unidade da métrica, e um indicador visual para itens `counted=false` (com o `excludedReason`).
- [x] 5.3 Tratar lista vazia e erro de carregamento sem quebrar o restante do drawer.
- [x] 5.4 Conferir paridade visual com o restante do drawer (tipografia, espaçamento, cores de estado já usadas em `prototype/index.html`/`static/index.html`).

## 6. Fechamento

- [x] 6.1 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
