## Why

Quem olha um número no dashboard não tem como saber **como ele foi produzido**. "Cycle Time: 58h"
não diz se são horas corridas ou úteis, se conta o item desde a criação ou desde o início do
trabalho, se é média ou mediana, nem quais itens entraram na conta. Sem isso, o número vira objeto
de fé: ou a pessoa confia cegamente, ou desconfia e para de usar — e as duas reações impedem que a
métrica leve a decisão nenhuma.

Isso vale duplamente para uma plataforma cuja decisão de produto é **medir para melhorar o sistema,
não vigiar pessoas** (`docs/initial-spec.md`). Uma métrica que ninguém entende não gera conversa
sobre o processo; gera desconfiança sobre a intenção.

Hoje existe meia solução: clicar num card abre um drawer com uma frase de definição (o `DEFS` do
`index.html`). Ela não é anunciada — não há nada indicando que é clicável —, é curta demais para
explicar um cálculo, cobre só os cards de métrica, e vive duplicada no frontend, longe do motor que
de fato calcula.

## What Changes

- Todo elemento que exibe um número ganha um **ícone "i"** visível, que abre um **modal explicando
  como aquele número é calculado**: a regra em texto claro, mais um **exemplo concreto** do cálculo.
- A explicação passa a ser servida pelo **catálogo de métricas** (`/api/metrics/catalog`), fonte
  única para toda a UI, em vez do `DEFS` duplicado no frontend.
- Cada explicação declara, além da prosa: **a fonte do evento** (commit, PR, work item, deploy),
  **quais eventos entram e quais ficam de fora**, **a estatística** (soma, mediana, razão,
  snapshot), **o período** e **como a atribuição funciona** (as-of-event).
- Gráficos e painéis que não têm card — tendências, heatmap comparativo, distribuição por tipo,
  cobertura, painel individual — passam a ter o mesmo ícone, explicando o que o eixo representa e
  como as séries são construídas.
- O drawer existente passa a consumir a mesma explicação, eliminando a duplicação.
- Um teste garante que **toda métrica do catálogo tem explicação** e que nenhuma explicação aponta
  para métrica inexistente.

## Capabilities

### New Capabilities

- `metric-explanation`: a explicação de como cada número exibido é calculado — conteúdo, onde é
  servida, e a garantia de que nenhuma métrica fica sem ela.

### Modified Capabilities

- `metric-drilldown`: o drawer deixa de ter definição própria e passa a exibir a explicação do
  catálogo, agora com regra detalhada e exemplo.

## Non-goals

- **Não** gerar a explicação a partir do motor nem exibir os números concretos daquela consulta
  ("mediana de 37 itens entre 01/06 e 07/06"). Decisão do usuário: texto curado, porque a
  prioridade é clareza para quem lê, não fidelidade automática ao código.
- **Não** mudar nenhum cálculo. Nenhum número muda de valor por causa deste change.
- **Não** internacionalizar. O texto é em português, como o resto da UI.
- **Não** adicionar edição das explicações pela tela de Admin. São conteúdo do produto,
  versionado com o código.
- **Não** documentar telas administrativas (usuários, repositórios, identidades) — elas exibem
  cadastro, não métrica.

## Impact

- **`domain`**: `MetricDefinition` passa a carregar a explicação (regra + exemplo).
- **`application`**: `MetricCatalog` ganha o texto de cada métrica; novo teste de completude.
- **`adapter-in-web`**: o catálogo passa a expor os campos novos; a SPA ganha o ícone e o modal,
  e perde o `DEFS` local.
- **Risco de manutenção**: texto curado pode divergir do cálculo. A mitigação está no design —
  e é a razão de o teste de completude existir.
