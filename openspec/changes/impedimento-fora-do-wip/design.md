## Context

A requirement de `impediment-signal` foi escrita quando o WIP ainda lia os intervalos de trabalho
(`spans`) — naquele desenho, não furar `spans` era exatamente o que mantinha o item bloqueado dentro
do WIP, e a requirement registrou isso como decisão. A change seguinte redefiniu o WIP como o
**trecho comprometido menos o impedimento** (`committed_spans`), a pedido do usuário. A premissa
caiu; o texto ficou.

Do outro lado, `container-filter` tem o cenário "A metric that cannot be filtered says so" escrito e
não implementado. A seleção já não alcança métrica que não é de work item
(`MetricSelections.filters(def, containers)`), mas isso é invisível: no Fluxo com "Folhas", `PR Size`
e `Commits` não se movem e nada explica. O filtro de tipo de trabalho já resolveu o mesmo problema
— `filterableByWorkType()` no domínio, o sinalizador no DTO, `filterNote` no card e
`unfiltered-col` na matriz.

## Goals / Non-Goals

**Goals:**
- A spec parar de afirmar duas coisas contrárias sobre o item impedido.
- A métrica que não responde à seleção de contêiner dizer isso onde o leitor olha.

**Non-Goals:**
- Rediscutir a decisão, mexer em cálculo, em ingestão ou em migração.

## Decisions

### D1. A spec antiga é corrigida, não revogada

A requirement continua existindo e continua sendo sobre o mesmo assunto — o que o impedimento faz
com as duas leituras. O que muda é a conclusão da segunda metade. Marcá-la como REMOVED e escrever
outra em outro lugar perderia o rastro de por que a regra é essa; corrigi-la no lugar deixa o
histórico no `archive` da change que a mudou.

A parte que **continua verdadeira** é dita explicitamente, porque é a que se confunde: os intervalos
gravados não mudaram. Mudou qual trecho o WIP lê.

### D2. "Responde ao filtro?" é derivado do tipo de evento, como já é para o tipo de trabalho

`filterableByContainer()` entra ao lado de `filterableByWorkType()` e é `eventType == WORKITEM`:
deploy pertence a um pipeline e pull request a um repositório, e nenhum dos dois agrupa work item.
Derivar do evento, em vez de declarar métrica a métrica, é o que evitou uma lista paralela para o
filtro de tipo e evita outra aqui.

`MetricSelections.filters(def, containers)` passa a chamar esse método em vez de repetir a condição
— a regra fica num lugar só, e o DTO que a tela lê é a mesma regra que o motor aplica.

### D3. O aviso usa os dois canais que já existem

Card: mais uma linha em `filterNote`, que já diz "não filtra por tipo — o deploy é do repositório".
Matriz: a coluna entra no mesmo conjunto que `colOff` já apaga. Inventar um terceiro jeito de dizer
"isto não responde ao filtro" seria a terceira variação do mesmo aviso na mesma tela.

## Risks / Trade-offs

- **Dois filtros ativos e dois avisos no mesmo card** → a nota pode ficar repetitiva num card DORA
  com as duas seleções ligadas. Mitigação: uma linha por seleção, texto curto, e a nota só aparece
  quando a seleção correspondente está ativa.
- **O leitor pode entender "não responde" como "está errado"** → o texto diz a razão (o evento não
  é work item), que é a mesma forma que já funcionou para o DORA.
