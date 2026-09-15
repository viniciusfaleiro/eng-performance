## Why

Hoje só existe um período visível: o corrente. Quem quer olhar julho — para preparar uma retro, para
entender por que um mês foi ruim, para comparar o antes e o depois de uma mudança de processo — não
tem como. A plataforma mostra a foto de agora e o gráfico de tendência, e a tendência responde
"como evoluiu", não "o que aconteceu naquele mês".

Isso limita justamente o uso que o produto existe para habilitar. A decisão de produto é **medir
para melhorar o sistema** (`docs/initial-spec.md`), e melhorar um sistema é uma conversa sobre um
intervalo concreto: "em julho o cycle time subiu — o que mudou ali?". Sem poder abrir julho, a
conversa para no gráfico.

Há também um custo prático: o período corrente é quase sempre **parcial**. Olhar "o mês" no dia 3
mostra três dias de dados, e é fácil confundir isso com queda de desempenho.

## What Changes

- A navegação ganha a escolha de **qual período** exibir, além de qual frequência. O controle
  acompanha a frequência selecionada: um **dia** (`15/07/2026`), uma **semana**
  (`Semana de 13/07`) ou um **mês** (`Julho/2026`).
- Setas `‹ ›` andam um período por vez; o rótulo abre a lista dos períodos recentes para saltar
  direto; um atalho volta ao período corrente.
- O período escolhido vale para **toda a navegação** — cards de DORA, Fluxo e IA, tendências,
  heatmap comparativo e painel individual. O período selecionado passa a ser o "agora" da tela, e a
  comparação "vs. período anterior" passa a ser contra o período imediatamente anterior a ele.
- A janela de tendência passa a **terminar** no período escolhido, em vez de terminar hoje.
- O drilldown de itens já aceita o período explicitamente; passa a receber o escolhido.
- A regra de comparação parcial deixa de valer quando o período escolhido já terminou: período
  completo compara contra período completo.
- O período selecionado viaja na URL, para que uma tela possa ser compartilhada como link.

## Capabilities

### New Capabilities

Nenhuma. Não é uma medição nova — é a mesma medição, ancorada em outro ponto do tempo.

### Modified Capabilities

- `metrics-navigation`: o shell passa a selecionar **período** além de frequência e nó, e o período
  viaja na URL.
- `metrics-engine`: o período de referência deixa de ser sempre "hoje" e passa a ser um parâmetro;
  a regra de comparação por fatia decorrida passa a valer apenas para o período em andamento.

## Non-goals

- **Não** permitir intervalos arbitrários ("de 12/03 a 27/06"). O período é sempre um balde da
  frequência escolhida — é o que mantém a comparação com o período anterior bem definida.
- **Não** comparar dois períodos escolhidos lado a lado. Isso é outra tela, não este controle.
- **Não** mudar nenhum cálculo de métrica. Os mesmos eventos, a mesma agregação, outro intervalo.
- **Não** criar exportação, agendamento ou snapshot histórico congelado. O número de julho é
  recalculado dos eventos a cada consulta, como hoje.
- **Não** limitar por retenção: se houver evento ingerido, o período é consultável. O limite
  prático é o backfill de 6 meses do adapter do ADO, e isso não muda aqui.

## Impact

- **`domain`**: a noção de "período selecionado" — um balde identificado por data de início e
  frequência, com validação de que não é futuro.
- **`application`**: `MetricsService` e os serviços de dashboard deixam de derivar `reference` do
  relógio e passam a recebê-lo; o relógio continua sendo a origem do **padrão**.
- **`adapter-in-web`**: um parâmetro de período nos endpoints de métrica, dashboard, heatmap e
  painel individual; o controle na SPA e a sincronização com a URL.
- **Compatibilidade**: o parâmetro é opcional em toda a API. Omitido, o comportamento é o de hoje —
  o período corrente.
