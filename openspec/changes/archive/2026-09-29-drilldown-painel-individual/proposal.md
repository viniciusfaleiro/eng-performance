## Why

No painel individual, os cards de entrega mostram o número e nada mais. "6 itens concluídos" não
diz *quais*, e é justamente a lista que torna a conversa de coaching possível: sem ela, gestor e
pessoa discutem um agregado que nenhum dos dois consegue conferir.

Os dashboards de estrutura já resolvem isso — clicar num card abre o drawer com os itens
considerados no cálculo, com link para o registro no Azure DevOps. O painel individual, que é
exatamente onde a verificabilidade mais importa, não tem esse caminho.

Isso também protege contra o uso errado do painel. Um número isolado convida a julgamento; a lista
de itens convida a perguntas — "esse aqui travou por quê?" —, que é o uso que o produto pretende.

## What Changes

- Cada card de entrega do painel individual passa a oferecer a abertura do detalhamento: os itens
  considerados no cálculo daquele número, para aquela pessoa e período.
- Vale para **Throughput**, **Commits**, **Pull Requests** e **% de commits com IA**, e também para
  **Cycle Time**, que compartilha o mesmo mecanismo.
- O detalhamento é o mesmo dos dashboards — mesma lista, mesmos links, mesma marcação de item que
  não contou e por quê.
- Selecionar o card (que troca a métrica do gráfico) e abrir o detalhamento continuam sendo gestos
  distintos, como o ícone de explicação já é.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `individual-dashboard`: os números de entrega passam a ser detalháveis item a item.

## Non-goals

- **Não** criar um endpoint novo: o detalhamento por nó já existe e já aceita uma pessoa como nó.
- **Não** afrouxar o acesso: quem não pode ver a pessoa individualmente continua sem ver o painel,
  e portanto sem a lista.
- **Não** mudar nenhum cálculo.

## Impact

- **`adapter-in-web`**: os cards selecionáveis ganham a abertura do drawer; o drawer passa a
  funcionar com o nó da pessoa exibida, em vez do nó da navegação.
- **Nenhuma mudança de backend** — é reuso do que já existe.
