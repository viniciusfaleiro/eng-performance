## 1. Domínio — convenção e álgebra de intervalos

- [x] 1.1 Criar `core/domain/src/main/java/com/engperf/domain/config/ImpedimentConvention.java`: record `(String fieldName, String tagPrefix)`, com um `isEmpty()`/`enabled()` que trate nulo e branco, e javadoc dizendo que `fieldName` é o *reference name* do campo no ADO. Sem Spring.
- [x] 1.2 Criar a álgebra de intervalos em `core/domain/src/main/java/com/engperf/domain/metrics/` (ao lado de `InProgressSpans`): união de intervalos `[from,to)` em lista disjunta ordenada, sobreposição total com um intervalo (`overlapMs`) e diferença (um intervalo menos a união). Puro, sem framework.
- [x] 1.3 Testar a álgebra com bordas: intervalos adjacentes (`to == from`), contidos, idênticos, disjuntos, lista vazia, intervalo degenerado (`from == to`), ordem de entrada embaralhada. Cobertura é gate em `domain`.

## 2. Configuração de plataforma (porta → persistência → admin)

- [x] 2.1 `PlatformConfigPort`: + `getImpedimentConvention()` / `saveImpedimentConvention(ImpedimentConvention)`; `PlatformConfigUseCase`: + `impedimentConvention()` / `saveImpedimentConvention(fieldName, tagPrefix)`; implementar em `PlatformConfigService` delegando ao port, no mesmo molde de `aiConvention()`.
- [x] 2.2 Criar `db/migration/V9__impediment_convention.sql` — `impediment_convention(id VARCHAR(16) PK, field_name VARCHAR(200), tag_prefix VARCHAR(64))`. **Não usar `V7`**: `V8` já está aplicado e o Flyway roda sem `out-of-order`.
- [x] 2.3 `ImpedimentConventionEntity` + repositório em `AdminJpaRepositories` + implementação em `JpaPlatformConfigRepository`, com default `(null, null)` quando a linha não existe (overlay desligado), espelhando `AiConventionEntity`.
- [x] 2.4 `AdminFixtures`: semear `fieldName="Impediment"`, `tagPrefix="Impedimento:"` de forma idempotente.
- [x] 2.5 `ConfigDtos` + `ConfigController`: `GET`/`PUT /api/admin/impediment-convention`, mesma autorização e forma do endpoint da convenção de IA.
- [x] 2.6 Atualizar os `FakeConfig` de `core/application` e de `adapters/out-ado` com os métodos novos (sem isso os testes dos dois módulos não compilam).
- [x] 2.7 Testes: `PlatformConfigServiceTest` (salvar/ler) e `AdminPersistenceTest` (Testcontainers, salvar/ler/atualizar); teste web do novo endpoint.

## 3. Ingestão — linha do tempo de impedimento e overlay

- [x] 3.1 Criar `adapters/out-ado/.../ImpedimentTimeline.java`: a partir do **mesmo** `updates` já buscado, reconstruir dois step functions — campo (`newValue` ≠ vazio/"None", case-insensitive) e tags (`System.Tags`, entrada/saída de tag com o prefixo configurado) — em intervalos `[from,to)`, unir pela álgebra de 1.2, e expor `overlapMs(from,to)` e os intervalos por motivo. Reusar a recuperação do sentinela `revisedDate = 9999-01-01` via `changed` que `WorkItemFlow.collectStates` já faz. Convenção vazia ⇒ timeline vazia.
- [x] 3.2 Motivo = sufixo do prefixo da tag, normalizado (`strip()` + lowercase); guardar a grafia original para a legenda; intervalo marcado só pelo campo ⇒ bucket reservado `?`. Garantir que a normalização elimina `?` de tags, para não colidir com o bucket.
- [x] 3.3 `ImpedimentTimelineTest`: toggle do campo; toggle da tag; os dois sinais sobrepostos (união, sem duplicar); vários toggles somando; `Impedimento:infra` → `infra`; `Impedimento:Infra` e `impedimento: infra` agrupam; campo com "None" não marca; convenção vazia ⇒ vazio; revisão vigente datada pelo `changed`.
- [x] 3.4 `WorkItemFlow.of(...)` ganha a convenção/timeline e aplica o overlay: em intervalo ACTIVE/REVIEW, `impededMs = overlapMs(from,to)` sai de ativo/review e entra em espera; intervalo WAITING **não** chama `overlapMs`. Manter o rastro DEBUG por id do work item, no padrão da classe.
- [x] 3.5 `WorkItemFlow.fill(detail)` grava `impeded:<motivo>` (horas), `impeded_label:<motivo>` (grafia original) e `impeded_spans` (`from:to,...`). **Não** gravar total agregado — a soma dos motivos é o total. Atribuir motivo também ao impedimento ocorrido dentro de estado WAITING, sem somar em `wait_h` de novo.
- [x] 3.6 `AdoMapper.workItem(...)` ganha o 9º parâmetro (a convenção) e repassa para `WorkItemFlow.of`; `AdoEventSource.fetchWorkItems` lê `config.impedimentConvention()` **uma vez**, antes do laço de montagem, e repassa. Nenhuma chamada HTTP nova.
- [x] 3.7 `WorkItemFlowTest`: impedimento parcial em ACTIVE (ativo cai, espera sobe); REVIEW inteiro impedido; impedimento cruzando fronteira ACTIVE→WAITING sem duplicar; impedimento dentro de WAITING (motivo atribuído, `wait_h` inalterado); invariante `Σ impeded:* ≤ wait_h`; `active_h + review_h + wait_h` preservado; `impeded_spans` escrito; convenção vazia ⇒ detail idêntico ao de hoje.
- [x] 3.8 Atualizar `AdoMapperTest` e `AdoMapperParentTest` para a assinatura nova, e acrescentar um caso ponta a ponta JSON → `detail` com convenção real (fixture nova de `updates` com campo e tag).

## 4. Quebra da Espera por motivo (aplicação → API → tela)

- [x] 4.1 Criar `core/application/.../metrics/ImpedimentBreakdown.java`: sobre a mesma população do `waiting_time` (reusar `MetricsEngine.match`/`inBucket` e o predicado de `MetricsEngine.restrict`), somar as chaves `impeded:*` por motivo, resolver o label, e calcular o residual `wait_h − Σ impeded:*` como fatia "Fila do board". Expor também quantos itens da população carregam algum sinal (cobertura).
- [x] 4.2 `FlowDashboardService.dashboard(...)` passa a devolver a quebra ao lado de `phases`; `FlowDashboard` e um record novo de fatia acompanham. Honrar filtro de tipo de trabalho e escopo de nó pelos caminhos que já existem.
- [x] 4.3 `FlowDtos` + `FlowDashboardController`: serializar a quebra e a cobertura no payload de `/api/dashboards/flow`.
- [x] 4.4 Testes: `ImpedimentBreakdownTest` (fecha com a Espera; residual presente; motivos com grafia diferente agrupam; cobertura contada) e `FlowDashboardServiceTest` (quebra respeita o filtro de tipo de trabalho e o escopo).
- [x] 4.5 SPA: `indexFlow` passa a preservar as keys das fases (hoje descarta em `phases.map(p=>p.hours)`) e a guardar a quebra; o segmento e o item de legenda da Espera em `phaseBlock` ganham `class="click"`, `data-phase`, `role="button"`, `tabindex="0"` e `aria-label`, ligados no padrão de `bindRowAffordance`.
- [x] 4.6 SPA: incluir `waiting_time` em `PROTO2METRIC` e renderizar, no drawer da Espera e acima da lista de itens, a quebra com `stackBlock(...)` + linhas `.hbar-row` (motivo, horas, share), a fatia residual e a linha de cobertura ("N de M itens têm sinal de impedimento"). Agrupar os motivos menores em "Outros" com teto de ~8 fatias.

## 5. Painel individual

- [x] 5.1 `WorkDistribution.tally(...)`: subtrair `impeded_spans` dos intervalos de `spans` (álgebra de 1.2) antes de montar os `Span` prorrateados. **Não** alterar `spans` na ingestão — `wip`/ocupação ficam intactos.
- [x] 5.2 `WorkDistributionTest`: tempo impedido sai do trabalho prorrateado; item sem `impeded_spans` inalterado; impedimento parcial recorta o intervalo em dois; `wip` (via `InProgressSpans` sobre `spans`) permanece igual.

## 6. Explicações (botões "i")

- [x] 6.1 Reescrever `ViewExplanations.cycle_time_phases`: o impedimento move tempo de Ativo/Review para Espera mesmo dentro de coluna ativa; exemplo numérico com um trecho impedido; na seção "Para o time usar bem", dizer que `Impedimento:<motivo>` é o que nomeia a fatia na quebra.
- [x] 6.2 Atualizar as explicações de `waiting_time`, `active_time`, `review_time` e `flow_efficiency` em `MetricCatalog`: o que entrou, o que saiu, e por que o Flow Efficiency cai; mencionar que períodos já sincronizados só mudam após reprocessamento.
- [x] 6.3 Acrescentar a explicação nova `impediment_reasons` em `ViewExplanations` (fonte do motivo, bucket "Sem motivo", residual "Fila", e que as partes somam a Espera) e pendurar o `infoBtn("view","impediment_reasons",...)` no bloco de quebra.
- [x] 6.4 Atualizar a explicação da distribuição do painel individual (`type_distribution`) e o texto do painel sobre a natureza das horas: tempo impedido não conta como trabalho.
- [x] 6.5 Texto de ajuda da Convenção de Impedimento em Admin, no padrão da Convenção de IA: o campo é o *reference name* (ex. `Custom.Impediment`), o prefixo é a tag, e deixar vazio desliga o overlay.
- [x] 6.6 Garantir `MetricCatalogExplanationTest` verde com a chave nova.

## 7. Fechamento

- [x] 7.1 Paridade visual: rodar o app com Postgres local, abrir o card de fases e o drawer da Espera, comparar por screenshot com o design system do protótipo (`.phasebar`/`stackBlock`) e iterar até ficar idêntico.
- [x] 7.2 Checagem ponta a ponta: configurar a convenção em Admin; sincronizar um work item com (a) tag `Impedimento:infra` dentro de estado ativo, (b) campo marcado sem tag, (c) trecho impedido dentro de estado de fila; conferir Flow Efficiency menor, Espera maior, as fases somando o cycle time, a quebra fechando com o card e o painel individual com menos horas e WIP inalterado.
- [x] 7.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL** (Spotless, Checkstyle 0 warnings, SpotBugs+FindSecBugs, JaCoCo 70/60 em domain+application, ArchUnit).
