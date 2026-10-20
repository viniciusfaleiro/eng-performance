## Why

No dashboard de Fluxo de um time real, na frequência mensal, quatro números na mesma tela não
conversam entre si:

| Onde | O que aparece |
|---|---|
| Card **Cycle Time** | 21,2 h |
| Card **"Cycle Time — fases (board)"** | Espera 0 h · Ativo 1,5 h · Review 0 h |
| Resumo **"Maior gargalo"** | Ativo, 1,5 h |
| Drawer da **Espera** | ~20.000 h distribuídas entre motivos |

São quatro causas, todas confirmadas no código:

1. **Item fechado direto entra nas fases com zero.** `cycle_h` só é gravado quando o item passou por
   estado de trabalho; sem ele, o item fica **fora** do card (medida ausente) mas **dentro** das
   fases com `active_h = wait_h = review_h = 0`, porque zero não é ausência. Num time que fecha
   muita task sem mover no board, esses zeros são maioria e derrubam as medianas das fases.
2. **Mediana por fase não soma o card.** Por item, ativo + review + espera = ciclo; mediana das
   partes ≠ mediana do todo. A própria spec se contradiz hoje: um requisito diz que as fases "need
   not sum", outro diz que elas "always add up to the value shown above them".
3. **O drawer da Espera está em outra escala.** Ele soma horas absolutas da população (daí as
   ~20.000 h) enquanto a fase ao lado é mediana por item. As duas nunca batem.
4. **Motivos simultâneos contam em dobro.** Cada motivo é recortado e somado independentemente, então
   um trecho com duas tags conta inteiro nas duas — e o residual é truncado em zero, escondendo a
   diferença.

Além disso, o resumo **"Maior gargalo"** nunca leu o dado real: ele usa a divisão sintética do
protótipo (pesos fixos + hash) aplicada ao Cycle Time, e ainda diz "h em média".

O que o usuário quer é direto: **ver o tempo médio gasto em cada fase, e esses valores baterem com o
Cycle Time do card**; e as horas do drawer baterem com a Espera do board.

## What Changes

- **As fases passam a usar a população do card de Cycle Time**: concluídos **que entraram em estado
  de trabalho**. Item fechado direto sai das fases (continua no Throughput).
- **Cada fase passa a ser a participação média dela no tempo dos itens, aplicada ao Cycle Time do
  card** — então as três **somam o card**. Substitui a mediana por fase.
- **O drawer da Espera passa a ser escalado para a Espera exibida**: as fatias somam exatamente a
  Espera do board. A soma bruta da população continua disponível como contexto, rotulada como tal.
- **Motivos simultâneos passam a repartir o trecho** igualmente na ingestão, com proteção na leitura
  para os dados já gravados.
- **"Maior gargalo" passa a ler as fases servidas pela API**, e diz "—" quando não há dado real.
- Explicações dos "i" reescritas para a regra nova.
- **BREAKING (leitura)**: os números das fases e do drawer mudam para todos os períodos, inclusive
  históricos, porque são calculados na leitura. A repartição de motivos simultâneos (ingestão) só
  vale para o que for sincronizado depois; até o reprocessamento, a proteção da leitura mantém o
  drawer fechando.

## Capabilities

### Modified Capabilities
- `flow-dashboard`: a população e o valor de cada fase mudam, as três passam a somar o card, o
  drawer passa a bater com a Espera exibida, e o resumo de gargalo passa a ler o dado real.
- `impediment-signal`: trecho coberto por vários motivos ao mesmo tempo é repartido entre eles.
- `metric-drilldown`: o drawer da Espera declara a Espera exibida como total das fatias.
- `metric-explanation`: as explicações das fases e da quebra por motivo passam a descrever a regra
  nova.

## Non-goals

- **Não mudar o valor do card de Cycle Time**, nem o Flow Efficiency, nem a classificação de estados.
- **Não mudar a extração do motivo** (texto depois do prefixo da tag configurada em Admin): os
  rótulos "Business", "devops" e afins são o que o time escreveu, não defeito.
- **Não tocar** no painel individual, no heatmap, na convenção de impedimento nem na persistência.
- **Nenhuma migração, dependência nova ou chave nova em `raw_event.detail`.**
- **Não reprocessar automaticamente** o histórico.

## Impact

- **application**: população das fases no catálogo; leitura nova de horas por fase; composição das
  fases e escala do drawer no serviço de Fluxo; `ImpedimentReasons`/`ImpedimentBreakdown` com total
  bruto e escala; explicações.
- **adapter-out-ado**: repartição de motivos simultâneos em `ImpedimentTimeline.hoursByReason`.
- **adapter-in-web**: `ImpedimentsDto` passa a declarar a Espera exibida e a soma bruta; o resumo de
  gargalo e os rótulos do drawer na SPA.
- Sem fronteira nova: ArchUnit não é tensionado.
