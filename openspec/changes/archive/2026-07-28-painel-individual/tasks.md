## 1. Domínio & catálogo (modules `domain`, `application`, sem Spring)

- [x] 1.1 `EventType.REVIEW` no enum
- [x] 1.2 Assertividade computada no service (first_pass/total dos PRs da pessoa) — sem entrada no catálogo (evita colisão de num/den do RATIO-em-PR com flow_efficiency)
- [x] 1.3 Constante da ordem/labels dos tipos de trabalho (feature/bug/tech_debt/maintenance/docs)

## 2. Aplicação — painel individual composto

- [x] 2.1 Records: `IndividualDashboard` + `CalendarDay`/`ReviewStats`/`WorkTypeSlice`/`ActivityItem` + porta inbound `IndividualDashboardUseCase`
- [x] 2.2 `IndividualDashboardService` (structure + events ports + `MetricsQueryUseCase` + Clock, espelhando `MetricsService`): resolve as identidades da pessoa; calendário = commits/dia (12 meses); assertividade = card `pr_assertiveness` do nó; entrega = séries `throughput`/`cycle_time`/`ai_share` do nó (reuso)
- [x] 2.3 Code review no service: reviews **dados** (REVIEW com reviewer ∈ identidades da pessoa → contagem, soma de comentários, aprovações, rejeições) e reviews **recebidos** (REVIEW com `detail.author` ∈ identidades da pessoa → contagem)
- [x] 2.4 Distribuição por tipo: WORKITEM da pessoa agrupados por `detail.type` → horas (soma de `detail.hours`) e share por tipo; atividade recente = últimos COMMIT/PR da pessoa com `detail.url`
- [x] 2.5 Testes de aplicação (fakes): calendário conta commits/dia; assertividade = first-pass/total; reviews dados vs recebidos com reviewer≠author; distribuição por tipo (horas+share); atividade com url

## 3. Adapter web (module `adapter-in-web`)

- [x] 3.1 `GET /api/individuals/{node}?freq=` + DTOs (calendar, assertiveness, delivery series, reviewStats, workTypes, activity)
- [x] 3.2 Enforcement coaching-only: aceita só nó `p:`; 403 se não `canViewIndividual` (admin ou conta gestora/própria)
- [x] 3.3 Testes de web (MockMvc): admin/gestor/própria conta → 200 com as seções; conta org-wide/exec sem gerência → 403

## 4. Seed & composição (module `bootstrap`)

- [x] 4.1 `EventFixtures`: PRs carregam `detail.first_pass` e `detail.url`; commits carregam `detail.url`; WORKITEM carrega `detail.type` e `detail.hours`; eventos REVIEW (reviewer, `detail.decision`, `detail.comments`, `detail.author`) — determinístico, datas ancoradas, sem random/now
- [x] 4.2 Wiring do `IndividualDashboardUseCase`
- [x] 4.3 Reseed único da `raw_event` (passo dev): `TRUNCATE raw_event` antes de subir para o seed novo (deploys/DORA/Fluxo/IA re-semeados iguais)

## 5. Frontend

- [x] 5.1 `viewIndividual` sai do sintético e lê `/api/individuals/{node}` — calendário de contribuição, gauge de assertividade, cards+trend de entrega, seção de code reviews (comentários/aprovações/rejeições + dados vs recebidos), distribuição por tipo (% + horas)
- [x] 5.2 Drawer de atividade lê os itens reais (commits/PRs) com **deep-link** para o Azure DevOps (`detail.url`)

## 6. Fronteiras, paridade e fechamento

- [x] 6.1 ArchUnit verde (domínio e application sem Spring; JPA só no adapter-out)
- [x] 6.2 Loop de paridade visual (logado como admin, navegando até uma Pessoa): **chrome** do painel individual (seções Atividade de commits/Entrega/Code reviews/Distribuição, calendário, gauge, cards, drawer) bate **0px** com o protótipo. Os **números** refletem o motor — divergem do mock por serem reais. Verificar também acesso coaching (própria conta/gestor vê; exec 403)
- [x] 6.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo 70%/60% em domain+application, ArchUnit); DORA/Fluxo/IA/Comparativo/S1-S7 intactos
