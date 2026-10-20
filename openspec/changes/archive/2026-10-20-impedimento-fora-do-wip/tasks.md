## 1. Spec coerente

- [x] 1.1 Promover o delta de `impediment-signal` no archive desta change: o tempo impedido sai do trabalho da pessoa **e** do trabalho em progresso, e a parte que continua valendo (os intervalos gravados não mudam) fica dita.
- [x] 1.2 Conferir que nenhuma outra spec promovida ainda afirma que item impedido conta no WIP (`grep -rn "impedi" openspec/specs/`).

## 2. A métrica diz quando a seleção não a alcança

- [x] 2.1 `MetricDefinition.filterableByContainer()` no domínio (`eventType == WORKITEM`), com teste, e `MetricSelections.filters(def, containers)` passando a usá-lo em vez de repetir a condição.
- [x] 2.2 O sinalizador viaja nos DTOs que a tela lê: card de métrica, card do dashboard de Fluxo e coluna do heatmap.
- [x] 2.3 SPA: `filterNote` ganha a linha do filtro de contêiner (só quando a seleção está ativa), e o heatmap marca a coluna que não responde, no mesmo `unfiltered-col` que o filtro de tipo já usa.
- [x] 2.4 Testes: o card de uma métrica de PR/deploy informa que não responde; a coluna correspondente vem marcada no payload do heatmap.

## 3. Fechamento

- [ ] 3.1 Conferir no app rodando, com "Folhas" selecionado no Fluxo, que PR Size e Commits dizem que não respondem — e que nenhum número mudou em relação a "Todos".
- [ ] 3.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
