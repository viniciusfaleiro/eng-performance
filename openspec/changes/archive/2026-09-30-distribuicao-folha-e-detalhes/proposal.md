## Why

A distribuição do trabalho soma itens-contêiner ao lado do próprio trabalho que eles agrupam. Num
caso real em homologação, uma janela de 30 dias (720 h corridas) devolveu **2.382 h**: uma Feature e
duas User Stories responderam por 1.768 h, quase a janela inteira cada, porque um contêiner fica "em
andamento" enquanto qualquer filha estiver aberta. Um total maior que o tempo que existiu no período
não é um número impreciso, é um número impossível — e destrói a credibilidade do card.

A mesma investigação mostrou o que **não** é o problema: excluir contêineres praticamente não move os
percentuais, porque um contêiner carrega a mesma categoria das filhas (99,996% → 99,98% de feature no
caso medido). A distribuição percentual daquela pessoa está certa em relação ao board; o que está
errado é o total. Vale corrigir o total, e vale parar de prometer que a pizza vai mudar.

Fica um segundo problema, que a correção agrava em vez de resolver: um Bug que passou 6 minutos em
"Active" — card movido depois de o trabalho estar feito — aparece como 0%. Hoje ele se esconde atrás
dos contêineres; depois da correção ele fica visível *e* parece preciso. Sem um sinal de convenção,
trocamos um número impossível por um número enganoso.

E, em três lugares do painel individual, o gestor vê um número agregado sem poder perguntar "quais?".
Um número que não pode ser auditado item a item vira objeto de desconfiança na primeira conversa de
coaching.

## What Changes

- A distribuição do trabalho passa a contar **apenas trabalho folha**: um work item que tem filhas
  ingeridas é contêiner e não entra no cálculo de horas. Epic nunca entra, com ou sem filhas.
- A ingestão passa a guardar o **id do pai** de cada work item, além do tipo do pai que já guarda.
  `System.Parent` já é lido hoje para resolver o tipo, e descartado em seguida.
- **BREAKING (dados, não API):** a regra só produz efeito depois de **reprocessar 6 meses**. Sem id
  do pai, nenhum item tem filhas conhecidas, logo tudo é folha e só o Epic sai. O comportamento antes
  do reprocessamento é o de hoje menos Epics — degradado, não quebrado.
- O painel passa a dizer que as horas são **corridas** (não horas trabalhadas) e que a hora de cada
  item é **dividida entre os itens simultâneos**.
- Novo alerta de convenção: itens que ficaram em andamento por poucos minutos, sinalizando card
  movido depois do trabalho feito.
- Três detalhamentos: a linha de um tipo de trabalho abre a lista de itens contabilizados; as linhas
  de reviews dadas e recebidas abrem a lista de reviews; os cards de entrega ganham um affordance
  visível para o detalhamento que já existe.

## Capabilities

### New Capabilities

Nenhuma. Tudo modifica capacidades existentes.

### Modified Capabilities

- `individual-dashboard`: a distribuição do trabalho passa a excluir contêineres; o painel passa a
  declarar a natureza das horas; reviews e distribuição passam a ser auditáveis item a item.
- `ado-integration`: o evento de work item passa a carregar o id do pai, para que "este item tem
  filhas?" seja respondível a partir dos nossos dados.
- `metric-drilldown`: o detalhamento deixa de valer só para métricas do catálogo e passa a valer para
  as listas do painel individual.

## Impact

- `adapters/out-ado`: `AdoMapper.workItem` (novo campo no detail).
- `core/application`: `IndividualDashboardService` (regra de folha, índice de filhos, convenção,
  novas listas), `ReviewStats`/`WorkTypeSlice` ou tipos irmãos para as listas.
- `adapters/in-web`: payload do painel individual, `docs/api/openapi.yaml`, e a SPA (linhas
  clicáveis, seta nos cards de entrega, legenda das horas).
- **Operação:** reprocessamento de 6 meses no deploy, sem o qual a correção não tem efeito.
