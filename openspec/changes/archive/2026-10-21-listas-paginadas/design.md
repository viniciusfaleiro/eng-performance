## Context

Quatro listas de itens vivem na SPA (`adapters/in-web/src/main/resources/static/index.html`), todas
renderizando `.act-list` inteira:

| Lista | Onde | De onde vêm os itens |
|---|---|---|
| Itens de uma métrica (inclui a Espera) | `loadDrawerItems` | `GET /api/metrics/{key}/items`, lista completa |
| Itens de um tipo de trabalho | `openWorkItemsDrawer` | payload do painel individual, **cortado** em `WorkDistribution.ITEM_LIMIT = 200` |
| Revisões | `openReviewsDrawer` | payload do painel individual |
| Atividade recente | `openActivityDrawer` | sintética (protótipo) |

O detalhamento por pessoa (`loadDrawerPeople`) **não** é uma lista de itens: ele mostra as duas
pontas de propósito, e paginá-lo mudaria o que a visão diz.

## Goals / Non-Goals

**Goals:**
- Dez itens por vez, com navegação e posição à vista, em toda lista de itens considerados.
- A lista continuar fechando com o número que explica.
- Lista cortada dizer que foi cortada, onde a navegação aparece.

**Non-Goals:**
- Mudar quais itens entram, a ordem, ou qualquer número.
- Paginação no servidor (contrato de API inalterado).
- Paginar o detalhamento por pessoa ou a quebra por motivo.

## Decisions

### D1. A paginação é de exibição, não de busca

As listas já chegam prontas do servidor. Paginar na exibição resolve o problema que o usuário tem
(ler e conferir) sem tocar em contrato, sem estado de cursor e sem uma segunda noção de "quais itens
contam". Se um dia a lista ficar grande demais para a rede — não é o caso hoje —, isso vira outra
decisão, com medida própria.

### D2. Um controle só, reusado nas quatro listas

A paginação entra como um helper de renderização ao lado de `act-list`, com a mesma marcação e o
mesmo comportamento de teclado em todas. Quatro paginações parecidas é como se chega a quatro
comportamentos diferentes para o mesmo gesto.

### D3. O corte de 200 passa a ser dito junto da navegação

Hoje o painel individual corta em `ITEM_LIMIT` e avisa numa frase acima das linhas. Com paginação,
a última página é exatamente onde o leitor conclui que a conta não fecha — então é lá que a frase
precisa estar. **O corte não é removido nesta change**: subi-lo ou tirá-lo é decisão de produto com
efeito em payload, e a change que o fizer tem de medir isso.

## Risks / Trade-offs

- **Dez por página pode parecer pouco numa lista de três itens** → sem navegação quando cabe tudo,
  então o custo é zero no caso pequeno.
- **A paginação não reduz o payload** → é deliberado (D1); o problema atacado aqui é de leitura.
- **Quatro listas, um controle** → se uma delas precisar de comportamento próprio depois, o helper
  vira o lugar errado; o risco é baixo porque as quatro são a mesma marcação hoje.
