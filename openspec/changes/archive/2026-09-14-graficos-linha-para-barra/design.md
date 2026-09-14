## Context

`lineChart(seriesArr, labels, unit, height)` é a única função de gráfico de série temporal do app —
SVG desenhado à mão, sem biblioteca externa (`prototype/index.html`/`static/index.html` seguem a
convenção de zero dependência de runtime de gráficos). Ela é chamada em 4 lugares (`areaTrend` no
hero de cada dashboard, o drawer de métrica, `viewTendencias`, e a comparação de Cycle Time com/sem
IA no dashboard de IA), sempre recebendo `seriesArr` (1 ou 2 séries `{name,color,pts}`), `labels`
(rótulos de período já calculados por `periodLabels`) e `unit` (pra formatar valores via `fmt`).
`sparkline(trend)` é uma segunda função, menor, para a mini-tendência de 32px dentro do card de
métrica — mesmo princípio, escala independente.

O eixo Y de `lineChart` hoje corta o mínimo com 15% de folga (`mn=Math.max(0,mn-pad)`) pra "zoomar"
na variação — o que funciona para uma linha (a posição Y carrega o valor) mas enganaria numa barra
(a altura relativa da barra é o que o olho lê como magnitude, e só é honesta partindo de zero).

## Goals / Non-Goals

**Goals:**

- Os 5 usos de `lineChart` (incluindo a comparação com/sem IA) e o `sparkline` do card passam a
  renderizar como barra, com eixo Y sempre ancorado em zero.
- Nenhum dado muda: os mesmos `pts`/`seriesArr` que já chegam até a função de gráfico continuam os
  mesmos; só a função de renderização muda.
- O tooltip por período (hoje via `<title>` nos hit-boxes) e a sinalização do período parcial
  (hoje um ponto na ponta da linha) são preservados, adaptados pro novo formato.
- Compensar a perda de leitura de variação que o eixo zerado causa em métricas de faixa estreita:
  o `%` de evolução ganha mais destaque visual perto do gráfico principal (`areaTrend`), que hoje
  não mostra nenhum número ao lado do título.

**Non-Goals:**

- Não adiciona biblioteca de gráficos (Chart.js, D3, etc.) — continua SVG à mão, mesma convenção.
- Não muda nenhum endpoint, DTO ou cálculo de métrica — puramente client-side.
- Não altera o scatter, o donut, o `comparativo()` (já barra horizontal) nem o `phaseBlock` de
  Cycle Time (já barra empilhada) — nenhum deles é `lineChart`.

## Decisions

### 1. `barChart(seriesArr, labels, unit, height)` substitui `lineChart` com a mesma assinatura

Os 4 call-sites de `lineChart` trocam só o nome da função — nenhum precisa mudar como monta
`seriesArr`/`labels`. Isso mantém o blast radius restrito à própria função de renderização.

### 2. Eixo Y sempre ancorado em zero

`mn=0` fixo (nunca `Math.max(0,mn-pad)`); `mx` continua com uma folga pequena acima do maior valor
(~10%) só pra não colar a barra mais alta no topo do gráfico — sem isso a leitura de proporção
entre barras fica desonesta, que é exatamente o motivo de trocar para barra em primeiro lugar.

### 3. Duas séries → barras agrupadas, não empilhadas

Quando `seriesArr.length===2` (comparação com/sem IA), cada período ganha duas barras finas lado a
lado (mesma cor de cada série, já definida pelo chamador) em vez de uma barra só. Empilhar
somaria as duas séries visualmente, o que não faz sentido pra uma comparação — a leitura correta é
"qual das duas é maior nesse período", não "quanto as duas somam".

### 4. Tooltip por período fica num hit-box por período, não por barra

Mantém o padrão já usado: uma faixa invisível (`<rect class="hit">`) cobrindo a largura de cada
período, com `<title>` listando o valor de todas as séries daquele período — igual ao que já
existe hoje nos hit-boxes de `lineChart`. Isso evita que o usuário precise acertar a barra fina
exata pra ver o tooltip, especialmente com barras agrupadas (2 barras estreitas por período).

### 5. Período parcial: estilo visual na última barra, não mais um ponto

Hoje a linha termina com um `<circle>` marcando o ponto atual. Como barra não tem "ponta", o
período parcial (quando o balde atual ainda não fechou — mesma lógica de `partial` que já existe
em `MetricsEngine`, refletida aqui via a última posição do array) ganha um estilo próprio: borda
tracejada (`stroke-dasharray`) na última barra, com a cor de preenchimento mais clara/translúcida.
Sinaliza "esse valor ainda pode mudar" sem precisar de um elemento novo.

### 6. `sparkline()` vira barras minúsculas, mesma área (200×32)

Mesma escala relativa (`min/max` do próprio trend, não zero-ancorada — é decorativa, não uma leitura
de magnitude absoluta, então mantém o comportamento atual de realçar a forma recente). Cada ponto
vira uma barra fininha; a última mantém destaque (cor mais forte ou leve realce), no lugar do
círculo que hoje marca o valor atual.

### 7. `evoChip` ganha lugar de destaque em `areaTrend`

Hoje `areaTrend(title, m)` só renderiza o título do gráfico — nenhum número ao lado. Passa a
calcular `snap(state.node, m, state.freq)` (o mesmo que os outros lugares já usam) e mostrar o
`evoChip` (o badge de evolução já existente, reaproveitado, não um componente novo) ao lado do
título, no `chart-head`, igual ao padrão já usado em `viewTendencias`. Isso devolve, em números, a
precisão de variação que o zoom da linha antiga dava visualmente de graça.

## Risks / Trade-offs

- **Métricas de faixa estreita ficam visualmente mais "achatadas"** (cycle_time oscilando entre
  55–60h vira barras quase do mesmo tamanho) → mitigado pelo `evoChip` mais visível (decisão 7) e
  pelos valores numéricos (`fmt`) já exibidos no tooltip e nas tiles — o gráfico deixa de ser a
  única fonte de leitura de variação, mas nunca foi a fonte de precisão mesmo (é ilustrativo).
- **Barras agrupadas em 12 períodos ficam mais densas que 2 linhas sobrepostas** (24 barras finas)
  → aceito conscientemente (decisão do usuário); o tooltip por período (decisão 4) compensa.
- **Nenhum teste automatizado cobre SVG renderizado** (não há suíte de teste de frontend no repo,
  por decisão de arquitetura documentada em CLAUDE.md) → validação é visual/manual, como já é hoje
  para toda a UI.

## Migration Plan

Troca direta, sem estado a migrar (nenhum dado persistido, nenhuma API muda). Deploy = servir o
`index.html` atualizado; rollback = reverter o arquivo. Sem passos intermediários.

## Open Questions

- Paleta de cor da barra "parcial" (tracejada/translúcida) — assumida como a mesma cor da série
  com opacidade reduzida, sem cor nova no design system.
