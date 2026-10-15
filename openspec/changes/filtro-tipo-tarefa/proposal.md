## Why

Hoje um time com cycle time de 60 horas não consegue dizer se isso é a feature que ele entrega ou o
bug que ele apaga — tudo entra na mesma mediana. O PRD (`docs/initial-spec.md`) já trata o **tipo de
trabalho** como dimensão de leitura (a distribuição por tipo existe no painel individual), mas ela é
hoje só uma fatia de pizza: não dá para ler o resto da plataforma por ela. Separar feature, bug,
tech debt e manutenção é o que transforma "o fluxo piorou" em uma conversa acionável sobre o
sistema, que é o propósito declarado da plataforma.

## What Changes

- **Novo filtro "Tipo de trabalho"** (multi-seleção) na topbar, ao lado de Frequência e Período,
  fazendo parte do estado global da navegação (nó · frequência · período · tipo) e da chave de
  cache — a seleção sobrevive ao pulo entre Dashboard, Tendências, Comparativo e painel individual.
- **Cinco categorias**, as mesmas que a plataforma já usa: `feature`, `bug`, `tech_debt`,
  `maintenance`, `docs`. O **de-para** dos tipos crus do Azure DevOps para essas categorias passa a
  ser explícito e declarado (hoje é um `switch` escondido no `AdoMapper`), e o tipo cru não mapeado
  deixa de se confundir com documentação real.
- **Fluxo filtra** nas métricas ancoradas no work item (Cycle Time, Throughput, Flow Lead Time, WIP,
  Flow Efficiency e as fases espera/ativo/review).
- **IA filtra** — e para isso o adapter do ADO passa a ingerir o **vínculo PR → work item**
  (`workItemRefs`), derivando o tipo de trabalho no evento PR e nos commits daquele PR. PR sem work
  item vinculado fica **fora** do resultado quando há filtro ativo, e isso aparece na cobertura em
  vez de desaparecer em silêncio. O mesmo vínculo habilita o filtro em `pr_review_time`, `pr_size` e
  nas métricas de código.
- **DORA não filtra.** Deploy é do repositório/pipeline e carrega várias tarefas de tipos
  diferentes; filtrar ali produziria um número que não quer dizer nada. O controle aparece
  desabilitado, com a razão dita na tela — não some sem explicação.
- **Comparativo**: o filtro vale para as colunas de Fluxo e IA; as colunas de DORA ficam
  **visualmente marcadas** como não filtradas, para o heatmap não misturar duas populações sem
  avisar.
- **Painel individual**: o filtro propaga para as séries de entrega, listas e atividade; a seção de
  **distribuição por tipo não é filtrada** — ela é o atalho que *liga* o filtro, e filtrá-la
  renderia um gráfico de 100% de uma fatia só.
- **Cobertura passa a ser a do denominador filtrado**: com filtro ativo, o card informa quantos
  eventos daquele tipo foram atribuídos, não quantos eventos no total.
- **Drill-down** (`/metrics/{key}/items`, `/metrics/{key}/people`) herda o filtro, para a lista
  aberta sempre bater com o card que a abriu.

## Non-goals

- **Não** se filtra DORA, por decisão de produto acima — nem agora nem atrás de uma flag.
- **Não** se cria tela de administração para editar o de-para ADO → categoria. O mapa fica
  declarado em um lugar só e visível no Admin · Integração ADO; torná-lo editável é outra change,
  quando houver evidência de que times precisam divergir.
- **Não** se muda a lista das cinco categorias nem se cria hierarquia de tipos (subtipos, labels,
  tags do board).
- **Não** se reclassifica histórico por outra regra: o reprocessamento apenas repassa os eventos já
  ingeridos pelo mesmo de-para e preenche o vínculo PR → work item.
- **Não** se filtra por tipo no cálculo de benchmark/tier DORA (consequência do primeiro item).

## Capabilities

### New Capabilities
- `work-type-filter`: a dimensão "tipo de trabalho" como filtro de leitura — o de-para declarado dos
  tipos do Azure DevOps para as cinco categorias, o contrato do parâmetro `workType` nas APIs de
  métrica, quais métricas são filtráveis e quais não são, o que acontece com evento sem tipo
  conhecido, e como a cobertura responde sob filtro.

### Modified Capabilities
- `metrics-engine`: a população de uma métrica passa a poder ser restrita por tipo de trabalho, e a
  cobertura sob filtro mede o denominador filtrado.
- `ado-integration`: a ingestão passa a gravar o vínculo PR → work item e a derivar o tipo de
  trabalho no evento PR e nos commits do PR; o de-para de tipos fica declarado e o tipo não mapeado
  deixa de ser indistinguível de `docs`.
- `flow-dashboard`: as métricas de Fluxo respondem ao filtro (as de board diretamente, as de código
  via vínculo do PR) e declaram sua filtrabilidade.
- `ai-dashboard`: as métricas de IA e as coortes com/sem IA respondem ao filtro, com a cobertura
  refletindo o PR sem vínculo.
- `comparison-heatmap`: o heatmap aplica o filtro às colunas filtráveis e marca as que o ignoram.
- `individual-dashboard`: o painel responde ao filtro, exceto a distribuição por tipo, que continua
  mostrando o todo e serve de atalho para o filtro.
- `metric-drilldown`: as listas de itens e de pessoas herdam o filtro ativo.
- `metrics-navigation`: o shell ganha o controle de tipo de trabalho no estado global, desabilitado
  com explicação onde não se aplica.

## Impact

- **Domínio/aplicação**: `MetricDefinition` passa a declarar se é filtrável por tipo;
  `MetricsEngine` recebe o predicado de população por tipo (mesmo gancho da coorte com/sem IA);
  `MetricsQueryUseCase` e os use cases de dashboard ganham o filtro na assinatura.
- **adapter-out-ado**: `AdoEventSource` busca os `workItemRefs` do PR; `AdoMapper` grava o tipo
  derivado no evento PR e nos commits; o de-para vira tabela declarada com o contador de tipos não
  mapeados apontando para ela.
- **adapter-in-web**: parâmetro repetível `workType` em `/api/dashboards/{flow,ai}`,
  `/api/metrics/cards`, `/api/metrics/{key}/series|items|people`, `/api/comparison/heatmap` e
  `/api/individuals/{node}`; respostas declaram o que foi filtrado. SPA: controle na topbar, estado
  e cache, marcação das colunas não filtradas no heatmap.
- **Persistência**: nenhuma migration nova — o tipo e o vínculo vivem em `raw_event.detail`
  (JSONB). Requer **resync/backfill** do ADO para os PRs antigos ganharem o vínculo; até lá eles
  contam como sem tipo.
- **Riscos**: cobertura de IA cai de forma visível para quem não vincula PR a work item (é a
  informação correta, mas vai gerar conversa); custo extra de requisições na sincronização de PRs.
