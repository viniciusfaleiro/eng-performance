## 1. Aplicação — catálogo & métricas (module `application`, sem Spring)

- [x] 1.1 Catálogo: adicionar `cycle_time` (MEDIAN/person, measure=cycle_h, LOWER_BETTER, h, group fluxo)
- [x] 1.2 Catálogo: adicionar fases `coding_time` (measure=coding_h), `pickup_time` (measure=pickup_h), `deploy_time` (measure=deploy_h) — MEDIAN/person, LOWER_BETTER, h; a fase review reusa `pr_review_time`
- [x] 1.3 Catálogo: adicionar `pr_size` (MEDIAN/person, measure=lines, LOWER_BETTER, linhas) e `flow_efficiency` (RATIO/person, HIGHER_BETTER, %)
- [x] 1.4 Constante da ordem das métricas Fluxo (cards) e das 4 fases (breakdown)

## 2. Aplicação — dashboard composto

- [x] 2.1 `FlowDashboard`/`FlowCard`/`PhaseBreakdown`/`ScatterPoint` (records) + porta inbound `FlowDashboardUseCase`
- [x] 2.2 `FlowDashboardService`: cards de Fluxo (valor+evolução+cobertura, sem tier) para o nó
- [x] 2.3 Breakdown de fases: as 4 medianas (coding/pickup/review/deploy) do nó
- [x] 2.4 Scatter: filhos do nó (all→verticais, vertical→times, time→sem scatter, pessoa→nunca) com throughput (x) e cycle_time (y)
- [x] 2.5 Testes de aplicação (fakes): cada métrica nova; cycle=mediana da soma; fases = mediana da população (as-of-event, cobertura); flow_efficiency=ativo/cycle; scatter sem pessoas; scatter vazio em nó=time

## 3. Adapter web (module `adapter-in-web`)

- [x] 3.1 `GET /api/dashboards/flow?node=&freq=` + DTOs (cards + fases + scatter)
- [x] 3.2 Enforcement de escopo reusando o `AccessScope`/filtro (403 fora de escopo; scatter só com nós visíveis; nunca pessoas)
- [x] 3.3 Testes de web (MockMvc): dashboard Fluxo em escopo (200) com cards+fases+scatter; nó fora de escopo (403); scatter não expõe pessoas
- [x] 3.4 Frontend: dashboard Fluxo sai do stub e lê `/api/dashboards/flow` — cards, cycle time (trend), rankPanel de cycle por filho, scatter throughput×cycle, phaseBlock das 4 fases

## 4. Seed & composição (module `bootstrap`)

- [x] 4.1 `EventFixtures`: cada PR carrega coding_h/pickup_h/review_h/deploy_h, cycle_h (=soma), lines, e detail.num=active_h / detail.den=cycle_h (determinístico, datas ancoradas, sem random/now)
- [x] 4.2 Wiring do `FlowDashboardUseCase`
- [x] 4.3 Reseed único da `raw_event` (passo dev): `TRUNCATE raw_event` antes de subir para o seed novo com fases substituir o do S4 (deploys/DORA re-semeados iguais)

## 5. Fronteiras, paridade e fechamento

- [x] 5.1 ArchUnit verde (domínio e application sem Spring; JPA só no adapter-out)
- [x] 5.2 Loop de paridade visual (logado como admin): **chrome** do dashboard Fluxo (topbar, árvore, breadcrumb, grid de cards, phaseBlock, rankPanel, scatter, layout) bate **0px** com o protótipo. Os **números** (cards, fases, ranking, scatter) refletem o motor — divergem do mock por serem reais (esperado; regra S3-S4-S5). Referência de 'hoje' ancorada (METRICS_REFERENCE_DATE)
- [x] 5.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo 70%/60% em domain+application, ArchUnit); DORA/S4 intactos
