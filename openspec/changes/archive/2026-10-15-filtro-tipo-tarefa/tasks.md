## 1. Domínio — tipo de trabalho como conceito de primeira classe

- [x] 1.1 Criar `WorkType` (enum das cinco categorias: feature, bug, tech_debt, maintenance, docs) em `core/domain`, com a chave e o rótulo que a UI já usa, e apontar `IndividualDashboardService.WORK_TYPES` para ele (uma lista só)
- [x] 1.2 Criar `WorkTypeMapping` em `core/domain`: tabela declarada tipo cru do ADO → `WorkType`, case-insensitive, incluindo as entradas de manutenção (maintenance/chore/support), com `isMapped(rawType)` derivado da própria tabela
- [x] 1.3 Criar `WorkTypeFilter` (conjunto de `WorkType`; vazio = tudo) com `matches(RawEvent)` lendo os tipos carimbados no evento, e testes cobrindo vazio, um tipo, vários tipos e evento sem tipo
- [x] 1.4 Declarar em `MetricDefinition` a filtrabilidade **derivada** do `EventType` (`filterableByWorkType()` = tipo ≠ DEPLOY), com teste garantindo que as quatro métricas DORA são as únicas não filtráveis

## 2. Motor de métricas

- [x] 2.1 Compor o predicado de tipo com o predicado de coorte existente em `MetricsEngine.series/card`, sem duplicar o caminho de população
- [x] 2.2 Contar e devolver `untypedExcluded` (eventos atribuídos sem tipo, descartados pelo filtro) junto de `MetricSeries`/`MetricCard`
- [x] 2.3 Passar a calcular cobertura sobre o denominador filtrado (eventos do tipo atribuídos / eventos do tipo), sem contar evento de outro tipo nem evento sem tipo como não coberto
- [x] 2.4 Estender `MetricsQueryUseCase` (`cards`, `series`, `cohortSeries`, `items`, `entityShares`) com o filtro, e `MetricsService` para ignorá-lo em métrica não filtrável marcando `filtered=false`
- [x] 2.5 Testes do motor: mediana/razão/contagem recalculadas sobre a população filtrada; filtro + coorte de IA compostos; filtro vazio idêntico ao comportamento atual; cobertura e `untypedExcluded` sob filtro

## 3. Ingestão do Azure DevOps

- [x] 3.1 Mover o `switch` de `AdoMapper.workType` para `WorkTypeMapping`, mantendo a herança de tipo pelo pai, e marcar o item cujo tipo cru não está na tabela como não mapeado (`type_unmapped`)
- [x] 3.2 `UnmappedTypes` passa a derivar da tabela e a expor os tipos vistos na última sync (contagem por tipo) para o Admin
- [x] 3.3 `AdoEventSource`: ler os work items vinculados a cada PR, resolvendo as categorias em lote junto do fetch de work items que a sync já faz (sem um pedido por PR)
- [x] 3.4 `AdoMapper`: gravar `work_types` (conjunto ordenado) no evento PR e propagar para os eventos COMMIT e REVIEW daquele PR; PR sem vínculo fica **sem** a chave
- [x] 3.5 Backfill: re-ingerir PRs já armazenados para preencher o vínculo, idempotente (mesmo id de evento, sem duplicar), acionável pelo Admin
- [x] 3.6 Testes do adapter: PR com um, vários e nenhum work item; propagação para commits e reviews; resolução em lote; tipo cru desconhecido marcado como não mapeado

## 4. API HTTP

- [x] 4.1 Parâmetro repetível `workType` em `/api/dashboards/flow`, `/api/dashboards/ai`, `/api/metrics/cards`, `/api/metrics/{key}/series|items|people`, `/api/comparison/heatmap` e `/api/individuals/{node}`, com 400 para valor fora das cinco categorias
- [x] 4.2 Respostas declaram o filtro aplicado e, por métrica/coluna, `filtered` e `untypedExcluded`
- [x] 4.3 Expor o de-para (tabela + tipos não mapeados da última sync) na API de administração da integração ADO
- [x] 4.4 Atualizar `api/openapi.yaml` e os testes de API (incluindo o 400 e o eco do filtro)

## 5. Dashboards compostos

- [x] 5.1 `FlowDashboardService`: aplicar o filtro aos cards, às fases e ao scatter dos filhos; agregar o `untypedExcluded` dos cards de código
- [x] 5.2 `AiDashboardService`: aplicar o filtro às duas coortes de impacto, ao share, à adoção e ao ranking de filhos, mantendo o escopo coaching-safe
- [x] 5.3 `ComparisonHeatmapService`: filtrar as colunas filtráveis e marcar as de DORA como não filtradas
- [x] 5.4 `IndividualDashboardService`: filtrar séries de entrega, listas e feed de atividade; manter a distribuição por tipo **sem** filtro
- [x] 5.5 Testes de aplicação para cada um dos quatro, incluindo o caso "filtro ativo + nenhum evento do tipo" (vazio explícito, não zero)

## 6. UI (SPA)

- [x] 6.1 Controle "Tipo de trabalho" (multi-seleção) na topbar, ao lado de Frequência/Período, reusando o design system do protótipo
- [x] 6.2 Estado global + chave de cache incluindo a seleção; seleção na URL compartilhável
- [x] 6.3 Desabilitar o controle com a razão visível na aba DORA, preservando a seleção ao voltar
- [x] 6.4 Marcar visualmente as colunas não filtradas do heatmap e os cards não filtráveis, com a explicação acessível
- [x] 6.5 Mostrar o `untypedExcluded` nas telas afetadas (Fluxo e IA), com destaque quando a fatia excluída for grande
- [x] 6.6 Clique na fatia da distribuição do painel individual liga o filtro correspondente
- [x] 6.7 Admin · Integração ADO: exibir o de-para e os tipos não mapeados da última sync
- [x] 6.8 Comparação visual com o protótipo até ficar idêntico (screenshot), verificando também por persona (não só admin)

## 7. Fechamento

- [x] 7.1 Item novo no checklist de convenções: "PR vinculado ao work item", com as métricas que dependem disso
- [x] 7.2 `./gradlew spotlessApply && ./gradlew build` verde (Spotless, Checkstyle, SpotBugs, JaCoCo, ArchUnit)
