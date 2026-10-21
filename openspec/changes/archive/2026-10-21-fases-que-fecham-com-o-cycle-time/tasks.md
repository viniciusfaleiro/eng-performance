## 1. População das fases

- [x] 1.1 `MetricCatalog`: predicado `cycled()` (`completed()` **e** `detail` com `cycle_h`) aplicado a `active_time`, `waiting_time` e `review_time`; `flow_efficiency` continua em `completed()`. Corrigir o comentário que afirma que as fases já usavam a população do Cycle Time.
- [x] 1.2 Teste: item concluído sem `cycle_h` não entra em nenhuma fase e continua no Throughput.

## 2. As fases somam o card

- [x] 2.1 `MetricsQueryUseCase`: leitura nova `phaseHours(nodeId, period, workTypes, containers)` devolvendo a soma das horas por fase sobre a população; `default` para quem não passa seleção, como nas demais.
- [x] 2.2 `MetricsService`: implementar com o mesmo `MetricsEngine.match` + `inBucket` + `MetricSelections.restrict` usados em `impediments(...)`, contando só eventos com medida presente.
- [x] 2.3 Helper novo ao lado de `ImpedimentBreakdown` (ex.: `PhaseComposition`) com a soma por fase, testável isoladamente.
- [x] 2.4 `FlowDashboardService`: cada fase passa a ser `cycleTimeCard × Σfase / Σtotal`; `Σtotal = 0` ou card ausente ⇒ as três são zero.
- [x] 2.5 Testes: E1 (300/100/100 com card 20 → 12/4/4) e E2 (8 fechados direto + 2 com ciclo → 35,0/2,5/0,5 somando 38) em `FlowDashboardServiceTest`; E7 com filtro de tipo e de contêiner.
- [x] 2.6 SPA: a barra de fases passa a exibir **horas e percentual** por fase na legenda (e no tooltip do segmento), para responder "quantos por cento do nosso tempo é espera?" sem conta na cabeça.
- [x] 2.7 Ajustar `cardsPhasesAndFlowEfficiency`, que hoje fixa as fases como mediana.

## 3. O drawer bate com a Espera

- [x] 3.1 `ImpedimentReasons`: `populationHours` (a soma bruta de hoje) e `scaledTo(waitingShown)`; `waitingHours`/`totalHours` passam a significar a Espera exibida.
- [x] 3.2 `ImpedimentBreakdown`: redução proporcional quando os motivos da população excedem a espera da população, com residual zero.
- [x] 3.3 `FlowDashboardService`: aplicar `scaledTo` com a Espera calculada em 2.4.
- [x] 3.4 `FlowDtos.ImpedimentsDto`: `totalHours` = Espera exibida, `populationHours` novo; javadoc.
- [x] 3.5 SPA: cada motivo exibido com **horas e percentual** da Espera (a barra empilhada e as linhas da lista), de modo que "quantos por cento da nossa espera foi infra?" se leia direto.
- [x] 3.6 SPA: o título do bloco passa a declarar a Espera exibida; a soma bruta, se mostrada, vem rotulada como contexto. Manter a linha de cobertura do sinal e a lista de itens com a espera real de cada item.
- [x] 3.7 Testes: E3 (1000 h de população → 4,8/3,2 sobre 8 h exibidas), E4 (motivos excedentes reduzidos), E6 (população sem horas), e os dois testes existentes que verificam `waitingHours` como soma da população.

## 4. Motivos simultâneos (ingestão)

- [x] 4.1 `ImpedimentTimeline.hoursByReason`: varrer trechos elementares e dividir cada trecho pelo número de motivos que o cobrem; trecho sem motivo vai para o bucket reservado.
- [x] 4.2 Testes em `ImpedimentTimelineTest`: E5 (duas tags por 10 h → 5/5; sobreposição parcial 0–10 e 6–10 → 8/2) e a invariante "soma dos motivos = horas impedidas nas janelas".

## 5. "Maior gargalo" lê o board

- [x] 5.1 `dashboardFluxo` (`index.html`): usar `flowPhases()` em vez de `phaseSplit`; nomear a fase de maior valor e mostrar esse valor; sem dado real, "—" e nenhuma fase nomeada.
- [x] 5.2 Trocar o sub-rótulo "h em média" por um que descreva o número (ex.: "h do Cycle Time"). `phaseSplit` fica só como fallback visual do `phaseBlock`.

## 6. Explicações ("i")

- [x] 6.1 `ViewExplanations.cycle_time_phases`: população (concluídos que entraram em trabalho; fechados direto ficam de fora porque não têm ciclo) e a regra da participação aplicada ao card.
- [x] 6.2 `ViewExplanations.impediment_reasons`: participação na espera aplicada à Espera exibida, motivos simultâneos divididos, "Sem motivo" e "Fila do board"; remover "mediana não se decompõe" e "as fatias compõem a soma da população".
- [x] 6.3 `MetricExplanations`: `waiting_time`, `active_time` e `review_time` com a mesma regra.
- [x] 6.4 Comentários de `phaseBlock`, `openWaitingDrawer` e `impedimentBlock` no `index.html`, e javadoc de `FlowDashboardService`, `PhaseSlice`, `ImpedimentBreakdown`, `ImpedimentReasons` e `FlowDtos`.
- [x] 6.5 `MetricCatalogExplanationTest` verde com os textos novos.

## 7. Fechamento

- [x] 7.1 Checagem ponta a ponta no app: num nó com dado real, Espera + Ativo + Review = Cycle Time do card; o drawer soma a Espera do board; "Maior gargalo" nomeia a fase do gráfico.
- [x] 7.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**. Se o Docker Hub recusar a imagem do Testcontainers neste host, rodar `./gradlew build -x :adapter-out-persistence:test` e **declarar isso explicitamente** — esta change não toca a persistência.
