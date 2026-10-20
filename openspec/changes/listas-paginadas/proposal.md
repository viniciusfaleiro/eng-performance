## Why

Toda lista de "itens considerados" do produto é renderizada **inteira**, de uma vez. No drill-down de
uma métrica de mês cheio isso são centenas de linhas numa gaveta de 540 px: o leitor rola sem
referência de onde está, não consegue voltar a um item que viu, e a lista que existe para **conferir
o número** vira uma parede.

Hoje existem quatro listas assim na tela — os itens por trás de uma métrica (inclusive a Espera), os
itens de um tipo de trabalho no painel individual, as revisões, e a atividade recente — e nenhuma
oferece navegação. A única defesa contra o volume é um **corte silencioso** no painel individual
(`ITEM_LIMIT = 200`), que corta o que passa disso e avisa em letra pequena: a lista deixa de fechar
com o número que ela explica, exatamente o oposto do que ela existe para fazer.

## What Changes

- **Toda lista de itens considerados passa a ser paginada**, mostrando **10 por página**, com
  navegação entre páginas e a posição atual à vista.
- **A lista continua fechando com o número que explica**: a paginação muda quantos itens aparecem
  por vez, nunca quais entram na conta. O total de itens continua declarado.
- **Uma lista cortada diz que foi cortada** e não oferece páginas além do que recebeu. Lista
  truncada que finge ter fim é pior que lista longa.
- Nenhuma mudança de cálculo: nenhum número se move por causa desta change.

## Capabilities

### Modified Capabilities
- `metric-drilldown`: a lista de itens de uma métrica passa a ser paginada, e a regra vale para toda
  lista de itens considerados que o produto mostra.
- `individual-dashboard`: as listas do painel (itens de um tipo de trabalho, revisões) passam a
  seguir a mesma paginação, e o corte existente passa a ser declarado onde a navegação aparece.

## Non-goals

- **Não mudar quais itens entram na lista**, nem a ordem, nem os números que elas explicam.
- **Não implementar paginação no servidor** nesta change: as listas já chegam prontas, e paginar a
  exibição resolve o problema do leitor sem mexer em contrato de API. Se o volume de rede virar
  problema, isso é outra decisão, com outra medida.
- **Não paginar o que não é lista de itens**: o detalhamento por pessoa, que já mostra as duas pontas
  de propósito, e a quebra por motivo, que é uma composição e não uma lista.

## Impact

- **adapter-in-web**: a SPA ganha a paginação nas listas (`loadDrawerItems`, itens por tipo de
  trabalho, revisões, atividade), com o mesmo controle reusado em todas.
- Nenhuma mudança em domínio, aplicação, ingestão ou persistência.
