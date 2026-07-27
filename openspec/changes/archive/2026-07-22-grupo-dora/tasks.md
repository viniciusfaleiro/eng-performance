## 1. Domínio (module `domain`, sem Spring)

- [x] 1.1 `MetricDefinition` ganha campo `measure` (String: "value" ou chave de detail), default "value" — não quebra métricas do S3
- [x] 1.2 `Tier` (ELITE|ALTO|MEDIO|BAIXO) + `TierBands` (limiares direction-aware por métrica) + avaliador `Tier.of(value, bands, direction)`
- [x] 1.3 Normalização de Deployment Frequency para deploys/dia (tier independente do bucket) no avaliador
- [x] 1.4 Testes de domínio: tier de cada métrica DORA nos 4 níveis (elite/alto/médio/baixo), direction-aware; DF normalizado dá o mesmo tier em Diário/Semanal/Mensal

## 2. Aplicação (module `application`, sem Spring)

- [x] 2.1 `MetricsEngine` lê a `measure` declarada para MEDIAN/SNAPSHOT (value ou chave de detail); eventos sem a medida saem da população daquela métrica
- [x] 2.2 Catálogo: adicionar `mttr` (MEDIAN/repo, measure=recovery_hours, LOWER_BETTER, unit h) + bandas de tier em deploy_freq/lead_time/cfr/mttr; migrar `cfr` para derivar num/den de `outcome`
- [x] 2.3 `DoraDashboardService` (use-case): compõe as 4 métricas como cards (valor + tier + evolução + cobertura) para um nó/frequência
- [x] 2.4 Ranking dos filhos do nó (all→verticais, vertical→times, time→sem ranking, pessoa→nunca), ordenado por valor respeitando direction, Top-N
- [x] 2.5 Porta inbound `DoraDashboardUseCase` (dashboard por nó/frequência)
- [x] 2.6 Testes de aplicação (fakes): as 4 métricas com tier correto; MTTR de par falha→recuperação (incl. as-of-event e cobertura); CFR conta recovery como não-falha; ranking sem pessoas; ranking vazio em nó=time

## 3. Adapter web (module `adapter-in-web`)

- [x] 3.1 `GET /api/dashboards/dora?node=&freq=` + DTOs (cards com tier/evolução/cobertura + ranking Top-N)
- [x] 3.2 Enforcement de escopo reusando o `AccessScope`/filtro do S2 (403 fora de escopo; ranking só com nós visíveis; nunca pessoas)
- [x] 3.3 Testes de web (MockMvc): dashboard DORA em escopo (200) com tiers e ranking; nó fora de escopo (403); ranking não expõe pessoas
- [x] 3.4 Frontend (protótipo servido): dashboard DORA sai do stub e lê `/api/dashboards/dora` — grid de cards com selo de tier, hero, ranking Top-N, tabela de tiers, stats de maiores/menores

## 4. Seed & composição (module `bootstrap`)

- [x] 4.1 `EventFixtures` emite DEPLOY com `detail.outcome` (success/failed/recovery); para cada `failed`, um `recovery` pareado no mesmo repo com `detail.recovery_hours` (determinístico, datas ancoradas, sem random/now)
- [x] 4.2 Wiring do `DoraDashboardUseCase`
- [x] 4.3 Reseed único da `raw_event` (passo dev): `TRUNCATE raw_event` antes de subir para o seed novo com outcome substituir o do S3

## 5. Fronteiras, paridade e fechamento

- [x] 5.1 ArchUnit verde (domínio e application sem Spring; `Tier`/avaliador puros; JPA só no adapter-out)
- [x] 5.2 Loop de paridade visual (logado como admin): **chrome** do dashboard DORA (topbar, árvore, breadcrumb, grid de cards, selos de tier, hero, ranking, tabela de tiers, layout) bate **0px** com o protótipo. Os **números** dos cards refletem o motor — divergem do mock por serem reais (esperado/desejado; mesma regra do S3, válida S4-S8). Referência de 'hoje' ancorada (METRICS_REFERENCE_DATE) p/ screenshots determinísticos
- [x] 5.3 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo 70%/60% em domain+application, ArchUnit)
