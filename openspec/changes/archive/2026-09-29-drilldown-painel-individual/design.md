## Context

`GET /api/metrics/{key}/items` já é node-aware e já passa pelo `requireView`, que para um id de
pessoa encaminha à regra de coaching (`AccessScope.canView` → `canViewIndividual`). O drawer dos
dashboards consome exatamente esse endpoint.

O que falta é ligação, não capacidade. Duas diferenças entre o painel individual e os dashboards
impedem o reuso direto:

1. Os cards do painel individual são **selecionáveis** (`data-im`, `aria-pressed`) — clicar troca a
   métrica do gráfico. Nos dashboards, clicar abre o drawer.
2. O drawer lê `state.node`, que no painel individual é a pessoa **quando** ela está selecionada na
   árvore — mas o painel também é alcançado por outros caminhos.

## Goals / Non-Goals

**Goals:**

- Que o número do painel individual seja verificável item a item, com o mesmo detalhamento de
  sempre.
- Não perder a seleção do gráfico, que é o gesto que já existe.

**Non-Goals:**

- Endpoint novo, mudança de acesso, mudança de cálculo.

## Decisions

### 1. Abrir o detalhamento é um gesto próprio, não o clique no card

O clique no card continua selecionando a métrica do gráfico. A abertura do drawer ganha um controle
próprio no cabeçalho do card, ao lado do ícone de explicação.

É a mesma escolha que fizemos para o "i": dois gestos no mesmo card pedem duas afordâncias. Trocar o
clique por "abrir drawer" custaria a seleção — que é o que o `aria-pressed` anuncia e o que o
gráfico abaixo usa —, e sobrepor os dois no mesmo clique deixaria o comportamento imprevisível.

O handler é delegado e em fase de captura, com `stopPropagation`, porque o card em volta tem o
próprio `keydown` que chama `preventDefault()` — foi exatamente o que quebrou a ativação por teclado
do ícone de explicação quando ele foi introduzido.

### 2. O drawer recebe a pessoa explicitamente

`openDrawer` passa a aceitar o nó a consultar, com o padrão continuando `state.node`. O painel
individual passa o id da pessoa exibida.

Sem isso, o drawer aberto a partir do painel de uma pessoa consultaria o nó da navegação — que pode
ser o time — e listaria itens de todo mundo sob o título da pessoa. Uma lista que discorda do número
acima dela é pior que não ter lista.

### 3. Cycle Time entra junto

Não foi pedido, mas o mecanismo é o mesmo e o card está na mesma fileira. Deixá-lo de fora criaria a
pergunta "por que só este não abre?" — inconsistência que custa mais que a linha de código que a
evita.

## Risks / Trade-offs

- **Mais um controle no card aumenta o ruído visual** → usa o mesmo peso do ícone de explicação
  (cor secundária, contraste no hover) e fica no cabeçalho, não competindo com o número.

- **A lista expõe títulos de work items e mensagens de commit da pessoa** → já é assim nos
  dashboards, e o acesso ao painel individual já é coaching-only. Não há superfície nova de dado,
  só de navegação.

## Migration Plan

Nenhuma.

## Open Questions

Nenhuma.
