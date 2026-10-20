## Why

Duas specs promovidas afirmam o contrário uma da outra sobre o mesmo caso. `impediment-signal`
diz que um item impedido **continua** contando como trabalho em progresso; `flow-dashboard`, promovida
depois, diz que um item impedido o período inteiro **não** conta. O código segue a segunda — é a
decisão do usuário, tomada depois, quando o WIP foi redefinido como o trecho comprometido menos o
tempo impedido. **A definição mais recente é a verdade**, e a spec antiga tem de dizer isso.

Enquanto as duas convivem, a spec deixa de ser a fonte que o repositório promete que ela é: quem
ler `impediment-signal` para decidir uma mudança vai implementar o oposto do que está rodando.

Junto disso, um cenário de `container-filter` está escrito e não implementado: a métrica que **não**
responde à seleção de contêiner precisa dizer isso. Hoje só a aba DORA avisa (o campo inteiro fica
apagado); no dashboard de Fluxo e no heatmap, com "Folhas" selecionado, as métricas de PR e de
volume simplesmente não respondem e nada na tela conta. É o mesmo defeito que o filtro de tipo de
trabalho já corrigiu uma vez, com a solução pronta ao lado.

## What Changes

- **A requirement contraditória de `impediment-signal` é reescrita** para dizer o que o produto
  decidiu e o código faz: o tempo impedido sai do trabalho da pessoa **e** sai do trabalho em
  progresso, porque o WIP passou a medir o trecho comprometido e não os intervalos de trabalho.
  O que continua valendo é que os **intervalos** gravados para a distribuição individual não mudam —
  o que mudou é qual trecho o WIP lê.
- **A métrica que não responde à seleção de contêiner passa a dizê-lo**, no card e na coluna do
  heatmap, no mesmo padrão do filtro de tipo de trabalho (nota no card, coluna marcada na matriz).
- Nenhuma mudança de cálculo: nenhum número se move por causa desta change.

## Capabilities

### Modified Capabilities
- `impediment-signal`: a requirement sobre trabalho em progresso passa a refletir a decisão mais
  recente, em que o tempo impedido também sai do WIP.

## Non-goals

- **Não rediscutir a decisão**: o tempo impedido fica fora do WIP, ponto.
- **Não mexer no cálculo** de nenhuma métrica, nem na ingestão.
- **Não alterar a spec de `container-filter`**: o cenário já está escrito e correto; o que falta é
  implementá-lo.

## Impact

- **domain**: a definição de métrica passa a dizer se responde à seleção de contêiner, como já diz
  para o tipo de trabalho.
- **adapter-in-web**: o sinalizador viaja nos DTOs de card e de coluna do heatmap; o card e a matriz
  passam a marcar o que não responde.
- Nenhuma mudança em ingestão, persistência ou migração.
