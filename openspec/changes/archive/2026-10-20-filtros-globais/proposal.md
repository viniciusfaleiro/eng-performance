## Why

A seleção de contêiner × folha só alcança duas das cinco telas. Medido endpoint a endpoint: o
dashboard de Fluxo e as métricas (cards, série, drill-down de itens) respondem; o **Comparativo**, o
**painel individual**, o **dashboard de IA** e o drill-down de pessoas **ignoram**. As três linhas do
heatmap vêm idênticas com `all`, `leaves` e `containers`; o throughput do painel individual não se
move.

Pior que a falta: **a tela envia o filtro para todos eles** — o parâmetro viaja junto do filtro de
tipo de trabalho — e o servidor descarta calado o que não declara. O leitor escolhe "Folhas", a tela
recarrega e os números são os mesmos, sem nada dizendo por quê. No heatmap chega a enganar: as
colunas de deploy e de PR são marcadas como "não respondem a este filtro", o que afirma que as
outras respondem — e nenhuma responde.

Um valor inventado (`container=epicos`) também é aceito com 200 onde o parâmetro não é declarado,
enquanto onde ele existe é 400. A mesma pergunta recebe duas respostas conforme a tela.

## What Changes

- **As duas seleções passam a ser globais**: tipo de trabalho e contêiner × folha alcançam todas as
  leituras que contam work item — Fluxo, métricas, Comparativo, IA, painel individual e o
  detalhamento de pessoas.
- **No painel individual, a distribuição de horas passa a responder à seleção de contêiner**: com
  "Todos" ou "Folhas" ela conta o trabalho folha, como sempre contou; com "Só contêineres" ela conta
  as horas dos itens que agrupam trabalho — que é a pergunta que o leitor fez ao escolher isso.
- **Valor desconhecido é 400 em qualquer endpoint**, nunca aceito em silêncio.
- A leitura padrão não muda em lugar nenhum: sem seleção, todos os números são os de hoje.

## Capabilities

### Modified Capabilities
- `container-filter`: a seleção passa a valer em toda leitura de work item, e não só nas telas onde
  foi plumbada primeiro.
- `individual-dashboard`: a regra de folha da distribuição passa a ser o padrão da seleção, e não
  uma decisão fixa da tela.

## Non-goals

- **Não mudar o default**: "Todos" continua sendo a leitura padrão em todas as telas.
- **Não fazer o DORA filtrar**: deploy pertence ao repositório e carrega trabalho de vários tipos;
  continua dizendo que não filtra.
- **Não mexer em cálculo de métrica** nem na ingestão.

## Impact

- **application**: os três use cases restantes (individual, heatmap, IA) e o detalhamento de pessoas
  passam a receber a seleção; a distribuição individual passa a consultá-la.
- **adapter-in-web**: `ContainerQuery` nos controllers que faltam — o que também traz o 400 para
  valor inválido de graça.
- Sem migração, sem mudança de contrato de leitura existente (campo novo só onde já havia o do tipo).
