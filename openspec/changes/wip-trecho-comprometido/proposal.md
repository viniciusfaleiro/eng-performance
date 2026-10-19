## Why

O WIP existe para expor **trabalho começado e não entregue** — é a alavanca que o time controla
diretamente e a causa mais comum de cycle time alto. Hoje ele não mede isso: ele reaproveita os
mesmos intervalos de Ativo+Review que o painel individual usa, e com isso fica cego justamente
onde o trabalho costuma travar. No board do time, um item parado em **Pronto p/ Prod** — feito e
não entregue, o estoque mais caro que existe — some do WIP; um item esperando em **Ready** nunca
entrou; e um item **impedido** continua contando como se estivesse andando.

Na direção oposta, o número é inflado por **contêiner**: enquanto um Epic fica em `Active` por três
meses, ele conta junto com todas as User Stories e Tasks dele, o mesmo trabalho somado duas e três
vezes. O dashboard de junho mostra **WIP 108** contra **Throughput 90**.

E, por ser contado por sobreposição, o WIP **não é aditivo** — o mesmo item conta em cada período
que atravessa. Mostrá-lo em Semanal e Mensal convida à leitura errada (somar os dias, comparar
períodos de tamanhos diferentes) sem que a tela tenha como impedir.

## What Changes

- **O WIP passa a contar o trecho comprometido do work item**: da entrada na **fila comprometida**
  (Ready; Active, nas Tasks) até o estado terminal, incluindo Doing, Code Review, Em homolog e
  Pronto p/ Prod. **Descontados os trechos impedidos** — o sinal de impedimento que já existe passa
  a fazer um segundo trabalho. Backlog (Refinement, New) e estados terminais ficam de fora.
- **A ingestão passa a gravar esse intervalo próprio.** Hoje o WIP lê o mesmo `spans` de Ativo+Review
  que a distribuição individual prorrateia; as duas leituras deixam de compartilhar o mesmo dado,
  cada uma com o intervalo que a sua pergunta exige.
- **O classificador de estados passa a separar "fila comprometida" de "backlog".** Hoje Refinement e
  Ready são ambos espera e nada os distingue. A separação entra por palavra-chave, no mesmo ponto
  onde as listas já são centralizadas, **sem renomear nada no board**.
- **Filtro global de contêiner**, três estados (Todos / Só contêineres / Só folhas), ao lado do
  filtro de Tipo de trabalho e viajando na URL como ele. **Default Todos**: nenhum número muda
  sozinho — o WIP de hoje só cai quando alguém escolher "Só folhas".
- **O WIP só aparece na frequência Diário**: some do card do dashboard de Fluxo, da lista de
  Tendências e do heatmap Comparativo nas demais frequências.
- **BREAKING (leitura)**: o WIP passa a significar outra coisa. Nenhuma API muda de forma
  incompatível, mas os números só mudam para o que for **sincronizado depois** — itens já ingeridos
  mantêm a leitura antiga até um reprocessamento.

## Capabilities

### New Capabilities
- `container-filter`: filtrar qualquer leitura de work item por contêiner × folha — tudo, só os
  itens que agrupam trabalho, ou só o trabalho em si — com a escolha viajando no endereço.

### Modified Capabilities
- `flow-dashboard`: o WIP muda de definição (trecho comprometido menos impedimento) e passa a ser
  oferecido só na frequência diária.
- `ado-integration`: a ingestão classifica fila comprometida à parte do backlog e grava o intervalo
  comprometido do item, descontado o impedimento.
- `metrics-engine`: a métrica de intervalo passa a declarar **qual** intervalo lê, em vez de haver
  um único intervalo de item para todas.
- `comparison-heatmap`: o WIP sai da matriz fora da frequência diária.
- `metric-explanation`: a explicação do WIP passa a descrever o trecho comprometido, o desconto do
  impedimento, o filtro de contêiner e por que a métrica só existe no diário.

## Non-goals

- **Não mexer nas fases do cycle time.** "Em homolog" continua classificado como Ativo; a separação
  entre fila comprometida e backlog existe para o WIP e não muda Espera/Ativo/Review.
- **Não mover o início do cycle time.** Ele continua no primeiro estado de trabalho (Doing),
  enquanto o WIP passa a começar no Ready: as duas métricas passam a cobrir trechos diferentes e a
  relação de Little deixa de fechar entre elas. É decisão registrada, não esquecimento.
- **Não reprocessar o histórico automaticamente**: a regra vale do próximo sync em diante.
- **Não alterar a distribuição do painel individual**, que continua lendo os intervalos de
  Ativo+Review.
- **Sem configuração nova em Admin**: a separação de estados é por palavra-chave no classificador,
  no mesmo seam que já existe.

## Impact

- **domain**: o catálogo passa a declarar qual intervalo a métrica de intervalo lê; nova dimensão de
  filtro (contêiner × folha) ao lado do filtro de tipo de trabalho.
- **application**: motor de métricas (membership por intervalo declarado), restrição de população
  pelo filtro novo, explicação do WIP.
- **adapter-out-ado**: classificação de fila comprometida, cálculo do trecho comprometido menos
  impedimento, chave nova no `detail` do work item.
- **adapter-in-web**: parâmetro do filtro nos endpoints que já recebem o filtro de tipo de trabalho;
  chip novo na barra de filtros; WIP escondido fora do diário no card, em Tendências e no heatmap.
- Sem migração, sem índice e sem coluna nova: `detail` é `jsonb` livre.
