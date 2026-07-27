## 1. Domínio (module `domain`, sem Spring)

- [x] 1.1 `RawEvent` (value object): type (COMMIT|PR|DEPLOY|WORKITEM), occurredAt (Instant UTC), repoKey?, committerIdentity?, numericValue?, phase?, isAi, detail (map); invariantes mínimas
- [x] 1.2 `MetricDefinition` + enums: attributionScope (PERSON|REPO), aggregation (SUM|MEDIAN|RATIO|SNAPSHOT), direction (HIGHER_BETTER|LOWER_BETTER), sentiment; unit, key, label, group
- [x] 1.3 Value objects de agregação: `Frequency` (DAILY|WEEKLY|MONTHLY) com bucketing ISO/segunda + UTC; `Bucket` (início/fim); `MetricValue` (valor, evolução, sentiment resolvido); `Coverage` (atribuídos/total)
- [x] 1.4 `MembershipTimeline` puro: resolve o time de uma Pessoa numa data (as-of-event) a partir do histórico de TeamMembership do S1
- [x] 1.5 Núcleo do roll-up puro (funções de agregação): SUM/MEDIAN/RATIO/SNAPSHOT sobre uma população de eventos; median/ratio recalculados da população (nunca compostos)
- [x] 1.6 Testes de domínio: cada tipo de agregação; median de time com pessoas de tamanhos diferentes; ratio ponderado; snapshot fim de bucket; as-of-event (pessoa que mudou de time); polaridade (lower-better caindo = bom)

## 2. Aplicação (module `application`, sem Spring)

- [x] 2.1 `EventStorePort` (inbound de ingestão / query de eventos por janela) + `MetricCatalogPort` (ou catálogo em memória semeado)
- [x] 2.2 Motor de agregação on-read: dado (metric, node, frequency) → resolve caminho de atribuição (person via CommitterIdentity→Person, repo via Repository→Team), aplica as-of-event, faz roll-up e bucketing
- [x] 2.3 Balde "Não atribuído" + cálculo de cobertura (eventos atribuídos / total no escopo)
- [x] 2.4 Comparação de período corrente parcial (mesmo trecho decorrido do período anterior); primeiro bucket sem anterior → evolução "n/a"
- [x] 2.5 Use-cases + ports inbound: `MetricsQueryUseCase` (catalog, cards por nó, series por métrica), respeitando o `AccessScope` do S2 (recebe o escopo, filtra/valida nó)
- [x] 2.6 Testes de aplicação (fakes): cards/series por nó nos 3 níveis; cobertura < 100% com evento não vinculável; caso "gestor sem commits" (contribui zero, agregado vem dos membros); negação fora de escopo

## 3. Adapter de saída (module `adapter-out-persistence`)

- [x] 3.1 Migration Flyway `V3__events.sql`: tabela `raw_event` (colunas comuns + detail jsonb) + índices por occurred_at/repo_key/committer_identity
- [x] 3.2 `RawEventEntity` + `EventStore` adapter (JPA) implementando o `EventStorePort`; mapeamento em `PersistenceConfiguration`
- [x] 3.3 Teste de integração do EventStore contra Postgres real (Testcontainers): grava e consulta por janela

## 4. Adapter web (module `adapter-in-web`)

- [x] 4.1 Controllers `/api/metrics/catalog`, `/api/metrics/cards?node=&freq=`, `/api/metrics/{key}/series?node=&freq=` + DTOs
- [x] 4.2 Enforcement de escopo nesses endpoints reusando o filtro/`AccessScope` do S2 (403 fora de escopo; individual coaching-only)
- [x] 4.3 Testes de web (MockMvc): cards/series de nó em escopo (200); nó fora de escopo (403); série individual de par (403); troca de frequência muda o resultado
- [x] 4.4 Frontend (protótipo servido): shell de navegação (topbar frequência Diário/Semanal/Mensal + seletor de visão) ligado aos endpoints; visão **Tendências** ligada a `/series`; grids DORA/Fluxo/IA permanecem stub

## 5. Composição & seed (module `bootstrap` / persistence)

- [x] 5.1 Wiring do `EventStorePort`, motor de agregação e use-cases de métricas
- [x] 5.2 Seeder idempotente de eventos sintéticos **determinísticos** (~6 meses, datas ancoradas, sem random/now): inclui pessoa com troca de time no meio da janela e eventos sem identidade/repo vinculável; `@Order` após o seed de estrutura/admin

## 6. Fronteiras, paridade e fechamento

- [x] 6.1 ArchUnit verde (domínio e application sem Spring; motor de agregação puro; JPA só no adapter-out)
- [x] 6.2 Loop de paridade visual (logado como admin): **chrome** do shell + Tendências (topbar, árvore, breadcrumb, seletor de visão, título, chips, legenda, frame do gráfico) bate **0px** com o protótipo. A **linha/eixo do gráfico** reflete dado real do motor — diverge do mock por ser real (esperado/desejado); paridade = fidelidade de design, não dos números sintéticos. Referência de "hoje" ancorada (data fixa) p/ screenshots determinísticos.
- [x] 6.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo 70%/60% em domain+application, ArchUnit)
