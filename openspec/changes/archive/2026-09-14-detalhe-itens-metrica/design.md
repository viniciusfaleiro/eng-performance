## Context

O drawer de métrica (`openDrawer` em `static/index.html`) mostra hoje o valor atual, a evolução e
um gráfico de 12 períodos — nada sobre **quais eventos** entraram na conta. O motor
(`MetricsEngine`) já sabe exatamente quais eventos alimentaram um número: seu método privado
`match()` filtra por atribuição (`StructureIndex.attribute`) e nó, e `aggregate()` decide, por
`Aggregation`, como esses eventos viram o valor final — mas essa informação nunca sai do motor. O
pedido concreto é de troubleshooting: quando um card não bate com o que o time vê no Azure
DevOps, hoje a única saída é ler código ou consultar o banco.

Restrições que moldam o desenho:

- **Nunca recompor um valor diferente do exibido**: a lista de itens tem que ser, literalmente, o
  subconjunto que `aggregate()` já usa — nunca uma segunda passada com lógica própria que possa
  divergir da agregação real.
- **RawEvent já carrega quase tudo**: `id`, `type`, `occurredAt`, `detail` (que já guarda `url` e
  `summary` para PR e commit). Falta só estender essa captura para work item/deploy/review.
- **`detail.url` vem do próprio ADO** (`_links.web.href` do payload), não de uma URL montada à
  mão — mais robusto a variações de topologia (cloud vs. Server on-prem).
- **RBAC por nó** já existe em todo endpoint de métrica (`MetricsController.requireView`); o
  endpoint novo segue o mesmo padrão.

## Goals / Non-Goals

**Goals:**

- Toda métrica do catálogo (DORA, Fluxo, IA) ganha uma lista de itens que é **exatamente** o que
  a agregação daquele tipo (`SUM`/`MEDIAN`/`RATIO`/`SNAPSHOT`/`DISTINCT_RATIO`) considerou.
- Cada item linka para o registro real no Azure DevOps, sempre que o tipo de evento tiver isso
  capturado.
- Itens que foram **excluídos** de uma métrica `MEDIAN` (por não carregarem a medida) aparecem
  sinalizados, não somem silenciosamente — é parte do troubleshooting saber que um item existe
  mas não contou.

**Non-Goals:**

- Não cobre eventos não atribuídos (fora do escopo do nó) — é a cobertura, não o cálculo.
- Não pagina nem filtra a lista — volume por bucket é pequeno o bastante.
- Não muda nenhum resultado de métrica.

## Decisions

### 1. `MetricsEngine.match()` passa a carregar o `RawEvent` de origem

O record privado `Matched` ganha um campo `RawEvent source` (hoje só guarda os números
derivados). Zero mudança de comportamento em `series`/`card`/`coverage` — só permite que um novo
método público, `items(...)`, tenha acesso ao evento original para montar o link/rótulo.

### 2. Um método por agregação, espelhando `aggregate()`

`MetricsEngine.items(index, events, def, nodeId, freq, reference, bucketStart, population)`:

1. Chama `match()` (mesma atribuição/medida que `series`/`card` usam).
2. Filtra pelo `Bucket` de `bucketStart` (mesmo `Frequency.lastBuckets` que a série usa —
   `bucketStart` é a mesma string que `SeriesDto.points[].bucket` já devolve, então o front pode
   clicar em qualquer ponto do gráfico, não só o período atual).
3. Seleciona o subconjunto **exatamente como `aggregate()` decide por `Aggregation`**:
   - `SUM`, `RATIO`, `DISTINCT_RATIO`: todo item do bucket entra, marcado `counted=true`.
   - `MEDIAN`: itens com `hasMeasure()` entram `counted=true`; os sem medida entram
     `counted=false` com `excludedReason="sem medida"` — mesma condição que `aggregate()` já
     filtra via `Matched::hasMeasure`.
   - `SNAPSHOT`: mesma dedução de `snapshot()` (último evento por entidade) — o vencedor de cada
     entidade fica `counted=true`; os demais eventos da mesma entidade no bucket ficam
     `counted=false`, `excludedReason="superado por evento mais recente da mesma entidade"`.
4. Mapeia cada `Matched` selecionado para `MetricDrilldownItem` (novo record em
   `application.metrics`, no padrão de `ActivityItem`): `eventId`, `eventType`, `url`, `label`
   (summary/title do detail, com fallback pro id), `entity` (a chave de atribuição —
   pessoa/time/repo), `occurredAt`, `measure` (o número que esse item carregou: horas, 1/0,
   linhas — o mesmo que `measure()` já calcula), `counted`, `excludedReason`.

A duplicação de switch (`aggregate()` decide o *valor*, `items()` decide *quais itens*) é
deliberada: são dois métodos pequenos e cada um already espelha exatamente sua contraparte —
introduzir uma abstração comum agora acopla dois usos com motivos de mudança diferentes (um
calcula, o outro explica) sem eliminar de fato a duplicação de conhecimento sobre cada
`Aggregation`.

### 3. `MetricDrilldownItem` mora em `application.metrics`, não em `domain`

Assim como `ActivityItem`, é uma projeção de leitura para a UI — não uma regra de negócio.
`domain`/`RawEvent` não mudam.

### 4. Endpoint `GET /api/metrics/{key}/items`

Parâmetros: `node`, `freq` (mesmos de `/series`) e `bucket` (opcional — a mesma string
`bucketStart` de um ponto da série; omitido = último bucket, o período "Atual" que o drawer já
mostra). Resposta: lista de `MetricDrilldownItemDto`. Mesmo RBAC de `MetricsController` (403 fora
do escopo do nó via `requireView`).

*Por que não embutir a lista dentro de `/series`*: a série tem 12 pontos e normalmente só um
interessa por vez (o drawer abre focado no atual); carregar 12 listas de itens a cada abertura de
drawer seria banda desperdiçada. Fica sob demanda: o front pede `items` só quando o usuário quer
o detalhe, e pode trocar de `bucket` sem recarregar a série inteira.

### 5. Captura de título/link no adapter do ADO

- **Work item**: `System.Title` entra no `fields` já pedido pelo `fetchWorkItems` (um campo a
  mais na mesma chamada batch, sem round-trip extra); o link é montado com o mesmo padrão de path
  já usado para a própria chamada REST do item — `org + "/" + enc(proj) + "/_workitems/edit/" +
  id` — já que a resposta de `workitems?ids=` não traz `_links.web.href` por padrão. `AdoMapper.
  workItem(...)` ganha os parâmetros `org`/`proj` (hoje já disponíveis no chamador
  `AdoEventSource.fetchWorkItems`).
- **Deploy**: reusa `webLink()` (o helper já existente para PR) sobre o `run` (o node de
  `_apis/build/builds`), que também expõe `_links.web.href` — mesmo padrão, sem helper novo.
- **Review**: `AdoMapper.reviews(JsonNode pr)` já recebe o node completo da PR; passa a chamar
  `webLink(pr)` também — o link de uma review é o link da própria PR (não existe uma tela de
  review isolada no ADO).

### 6. Item sem medida some do card mas não da lista

Um evento que casa com a métrica mas não tem a medida (`MEDIAN`) já é hoje silenciosamente
ignorado pelo `aggregate()`. Para troubleshooting, isso é exatamente o tipo de coisa que precisa
aparecer: "esse item existe, casou com a métrica, mas não contou porque não tinha X". Por isso
`items()` inclui esses itens com `counted=false`, em vez de replicar o filtro do `aggregate()` e
escondê-los também na lista.

## Risks / Trade-offs

- **Lista pode ficar grande em nós de alto volume/mensal** (ex. "Visão geral" com `throughput`
  mensal de uma vertical inteira) → aceito como não-goal desta change; paginação fica para depois
  se o uso real mostrar necessidade.
- **`_links.web.href` pode faltar em alguma resposta** (permissão do PAT/conta, versão do
  Server on-prem) → o item aparece sem link, com `url` vazio; a UI trata isso mostrando só o
  rótulo, sem quebrar (mesmo comportamento que `ActivityItem` já tem hoje para eventos sem url).
- **Work items antigos, já ingeridos antes desta change, não têm `System.Title` no `detail`
  salvo** → a migração não re-ingere o histórico; o rótulo cai para o id (`"Work item wi:1234"`)
  até o próximo sync trazer o campo. Aceitável: não é um dado perdido, é um dado que passa a ser
  capturado dali para frente, como qualquer novo campo de `detail`.
- **Duplicação da lógica de seleção por `Aggregation`** entre `aggregate()` e `items()` (decisão
  2) → mitigado por manter os dois métodos pequenos, cobertos por teste, e por um comentário
  cruzado em cada um apontando para o outro.

## Migration Plan

1. Estender `AdoMapper` (work item/deploy/review) e `AdoEventSource.fetchWorkItems` — sem
   migration de banco (é só um `detail` a mais no evento já persistido como JSON).
2. `MetricsEngine.items()` + `MetricDrilldownItem` + `MetricsQueryUseCase.items(...)`.
3. `MetricsController` + DTO + `docs/api/openapi.yaml`.
4. UI: seção nova no drawer, carregada sob demanda.
5. Rollback: reverter o binário não deixa estado inconsistente — o campo `detail.url`/`summary`
   novo em eventos já ingeridos simplesmente para de ser lido; nada é migrado para trás.

## Open Questions

- Se o rótulo do item deve mostrar o tipo de work item (Bug/Task/Story) junto do título — assumido
  que sim, já que `detail.type` já existe e ajuda a diferenciar itens na lista.
