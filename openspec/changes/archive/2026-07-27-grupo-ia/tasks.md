## 1. Motor — novas capacidades de agregação (module `application`, sem Spring)

- [x] 1.1 Agregação `DISTINCT_RATIO`: valor = pessoas distintas atribuídas que casam um predicado por-evento / pessoas distintas ativas, no nó e período; atribuição pelo caminho pessoa + as-of-event, contando cada pessoa uma vez por subárvore
- [x] 1.2 Filtro de população por atributo do evento: avaliar uma métrica-base só sobre os eventos cujo `detail` casa um par chave/valor (coorte), sem alterar a definição da métrica; cobertura reflete a fração da população com o atributo
- [x] 1.3 Testes de motor (fakes): distinct-ratio conta pessoa uma vez e faz roll-up sobre pessoas distintas da subárvore (não média de razões); mover de time não duplica; coortes complementares particionam a população (nenhum evento em ambas)

## 2. Aplicação — catálogo & dashboard composto

- [x] 2.1 Catálogo: reusar `ai_share`; adicionar `ai_adoption` (DISTINCT_RATIO, person, HIGHER_BETTER, %) e `ai_impact` (cycle_time por coorte de IA, HIGHER_BETTER = quão mais rápido com IA)
- [x] 2.2 Constante da ordem dos cards IA (aic → aad → aim) espelhando o protótipo
- [x] 2.3 `AiDashboard`/`AiCard`/`AdoptionRank`/`CohortSeries` (records) + porta inbound `AiDashboardUseCase`
- [x] 2.4 `AiDashboardService`: cards IA (valor+evolução+cobertura, sem tier); ranking de adoção dos filhos do nó (coaching-safe, all→verticais, vertical→times, time→sem ranking, nunca pessoas); donut com IA vs sem IA; série cycle time coorte IA vs não-IA (via filtro do 1.2)
- [x] 2.5 Testes de aplicação (fakes): cada métrica nova; adoção = pessoas distintas com IA / ativas; impact = delta cycle IA vs não-IA; uma coorte vazia → sem comparação; ranking sem pessoas; escopo respeitado

## 3. Adapter web (module `adapter-in-web`)

- [x] 3.1 `GET /api/dashboards/ai?node=&freq=` + DTOs (cards + trend %-com-IA + ranking adoção + donut + série coortes)
- [x] 3.2 Enforcement de escopo reusando o `AccessScope`/filtro (403 fora de escopo; ranking/donut/série só com nós visíveis; nunca pessoas)
- [x] 3.3 Testes de web (MockMvc): dashboard IA em escopo (200) com cards+trend+ranking+donut+série; nó fora de escopo (403); ranking não expõe pessoas
- [x] 3.4 Frontend: aba IA sai do stub e lê `/api/dashboards/ai` — cards, area trend de %-com-IA, rankPanel de adoção por filho, donut com/sem IA, série cycle time com IA vs sem IA

## 4. Seed & composição (module `bootstrap`)

- [x] 4.1 `EventFixtures`: cada PR carrega `detail.ai` (assistido-por-IA derivado dos commits do PR pela convenção do platform-config); commits já carregam a marca de IA (determinístico, datas ancoradas, sem random/now)
- [x] 4.2 Wiring do `AiDashboardUseCase`
- [x] 4.3 Reseed único da `raw_event` (passo dev): `TRUNCATE raw_event` antes de subir para o seed novo com `detail.ai` nos PRs (deploys/DORA/fases re-semeados iguais)

## 5. Fronteiras, paridade e fechamento

- [x] 5.1 ArchUnit verde (domínio e application sem Spring; JPA só no adapter-out)
- [x] 5.2 Loop de paridade visual (logado como admin): **chrome** da aba IA (topbar, árvore, breadcrumb, grid de cards, area trend, rankPanel de adoção, donut, série com/sem IA, layout) bate **0px** com o protótipo. Os **números** refletem o motor — divergem do mock por serem reais (esperado; regra S3-S4-S5-S6). Referência de 'hoje' ancorada (METRICS_REFERENCE_DATE)
- [x] 5.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo 70%/60% em domain+application, ArchUnit); DORA/Fluxo/S1-S5 intactos
