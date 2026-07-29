## 1. Classificação de estado (adapter-out-ado)

- [x] 1.1 Buscar a metadata de estados por tipo de item (`.../_apis/wit/workitemtypes/{type}/states`), cacheada por `(projeto, tipo)`, para mapear nome de estado → categoria ADO
- [x] 1.2 Classificador de estado: categoria (`Proposed`/`InProgress`/`Resolved`/`Completed`/`Removed`) quando presente, com fallback por nome; expor "é andamento?" e "é terminal?"

## 2. Time-in-state a partir do histórico (adapter-out-ado)

- [x] 2.1 `AdoEventSource.fetchWorkItems`: para cada item do delta incremental já calculado (`collectChangedWorkItemIds`), buscar `GET .../workitems/{id}/updates?api-version=7.1` (uma chamada por item, sem batch)
- [x] 2.2 `AdoMapper`: nova função que reconstrói, das transições de `System.State` (revised timestamp + antes/depois), a duração acumulada nos estados de **andamento**
- [x] 2.3 Emitir o `RawEvent` WORKITEM (id `wi:{id}`) com `numericValue` = duração em andamento e `detail.type`; itens sem transição utilizável recebem marcador de "sem dado" (medida ausente), continuando visíveis mas fora do valor
- [x] 2.4 Testes de mapeamento (fixtures de `updates`): New→Active→Resolved→Closed → duração de andamento correta por tipo; item sem transição → "sem dado" (não zero); categoria via metadata e via fallback por nome

## 3. Métricas passam a ler a nova medida (application)

- [x] 3.1 WIP (`MetricCatalog`): passa a somar a duração em andamento do WORKITEM em vez do `hours` de `CompletedWork`; itens "sem dado" fora do valor e refletidos na cobertura (não zero)
- [x] 3.2 `IndividualDashboardService.workTypes()`: distribuição por tipo usa a duração em andamento derivada; "sem dado" fora da distribuição e na cobertura
- [x] 3.3 Aposentar o uso de `CompletedWork`/`hours` nessas duas métricas (sem afetar Cycle Time, Throughput, PR Review Time, PR Size, Flow Efficiency, DORA, IA)
- [x] 3.4 Testes de aplicação: WIP e distribuição refletem time-in-state; itens sem dado abaixam a cobertura em vez de contarem zero

## 4. Fechamento

- [x] 4.1 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde em todos os gates (Testcontainers, ArchUnit, JaCoCo); S1–S10 intactos
- [ ] 4.2 Aceite manual (fora do CI): sincronizar contra o ADO real e ver WIP e a distribuição por tipo deixarem de ficar vazios, com a cobertura sinalizando itens sem histórico de estado
