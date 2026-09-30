## Context

`Period(Frequency, LocalDate start)` é hoje, por invariante, **um balde**: o construtor recusa um
`start` que não seja o primeiro dia do balde daquela frequência. Desse invariante saem três coisas:

- `MetricsService.fetch` busca `BUCKETS` baldes terminando no período;
- `MetricsEngine.series` monta os pontos com `Frequency.lastBuckets`, e o último ponto vira o card;
- a comparação usa o balde anterior, com a regra de fatia decorrida quando o balde está em curso.

Além disso, o DORA divide por dias do balde (`bucketDays`) para expressar frequência de deploy por
dia, e o frontend replica esse divisor (`freqDays`).

`Period` aparece em 23 arquivos.

## Goals / Non-Goals

**Goals:**

- Calcular qualquer métrica sobre um intervalo arbitrário, com a mesma agregação de sempre.
- Manter dia/semana/mês funcionando exatamente como hoje, inclusive a comparação por fatia
  decorrida no período em curso.
- Que o gráfico continue legível num intervalo longo.

**Non-Goals:**

- Comparar dois intervalos escolhidos, intervalo futuro, mudança de cálculo. Ver `proposal.md`.

## Decisions

### 1. O intervalo é o conceito; o balde vira um caso dele

Em vez de um tipo novo ao lado de `Period`, `Period` passa a ser **um intervalo** — início, fim
exclusivo e a frequência que serve de granularidade. Um balde continua sendo construível pela
frequência, e passa a ser um intervalo cujas bordas coincidem com as do balde.

Isso mantém os 23 call sites falando de uma coisa só. Um tipo paralelo obrigaria cada serviço a
decidir qual dos dois recebeu — e seria só uma questão de tempo até um deles tratar mal um dos
casos.

`Period` ganha `isBucket()`: verdadeiro quando as bordas coincidem com o balde da frequência. É essa
pergunta, e não o tipo, que decide o que muda de comportamento.

### 2. O período anterior é o intervalo anterior de mesma duração

Para um balde, isso dá exatamente o balde anterior — o comportamento de hoje sai preservado sem
caso especial. Para um intervalo livre de 45 dias, dá os 45 dias imediatamente anteriores.

É a única definição que não inventa informação. Comparar 45 dias com "o mês anterior" misturaria
durações e tornaria a variação sem sentido.

### 3. A fatia decorrida só existe para balde em curso

A regra que compara "10 dias de junho contra os 10 primeiros de maio" existe porque um balde em
curso é incompleto. Um intervalo que a pessoa escolheu é, por definição, o que ela pediu — mesmo
terminando hoje. Aplicar a fatia ali recortaria o intervalo sem que ninguém tenha pedido.

Então: `isBucket() && inProgress(hoje)` mantém a regra; qualquer outro caso compara em cheio.

### 4. A frequência muda de papel, e isso precisa aparecer na tela

Com balde selecionado, a frequência escolhe o balde. Com intervalo, ela fatia o gráfico. É a mesma
palavra com dois significados, e é a parte mais fácil de o usuário entender errado.

A mitigação é de interface: com intervalo ativo, o rótulo da frequência diz que ela é a
granularidade do gráfico, e o controle de período mostra as duas datas em vez de um balde.

*Alternativa considerada:* fatiar sempre em 12 partes iguais, ignorando a frequência. Rejeitada:
produz eixos sem significado de calendário ("blocos de 9 dias"), e é justamente o eixo que torna o
gráfico lido de relance.

### 5. A janela móvel é um intervalo com preset, não um terceiro conceito

"Últimos 7 dias" e "últimos 30 dias" são intervalos cujas bordas o sistema calcula em vez de o
usuário digitar. Nada no motor precisa saber que existem: chegam como intervalo, e todas as regras
acima já valem — comparação contra os 7 ou 30 dias anteriores, série fatiada pela frequência,
sem fatia decorrida (a janela está sempre cheia).

Essa é a razão de o alternador caber nesta mudança e não na seguinte: ele é uma etiqueta sobre o
mecanismo que já está sendo construído. Implementá-lo depois significaria mexer no mesmo controle
duas vezes.

O que muda de comportamento é só a navegação: no calendário a seta anda um balde; na janela móvel
anda a duração da janela. Em ambos, a âncora continua sendo o período apontado — o que preserva a
navegação para o passado que já existe.

**Diário não tem alternador.** "Último 1 dia" e "hoje" são o mesmo intervalo, e oferecer uma escolha
que não muda nada ensina o usuário a desconfiar dos controles.

### 6. A janela de busca de eventos passa a ser a do gráfico

Hoje `fetch` busca `BUCKETS` baldes para trás. Com intervalo, precisa buscar o intervalo inteiro
mais o anterior (para a comparação). Derivar a janela do que a série vai desenhar, em vez de um
número fixo de baldes, faz os dois casos caírem na mesma regra.

### 7. Dias do intervalo, não dias do balde

`bucketDays` do DORA passa a ser a duração do intervalo. Um `deploy_freq` sobre 45 dias divide por
45 — o que já era a intenção da métrica ("por dia"), apenas expressa sem depender do balde.

O frontend tem a mesma conta duplicada em `freqDays`. Com intervalo livre ela quebra, então passa a
vir do servidor junto do período resolvido — eliminando uma das duplicações que já nos custou bugs.

## Risks / Trade-offs

- **"vs. período anterior" fica ambíguo num intervalo** → o rótulo passa a dizer contra o quê
  compara (as datas do intervalo anterior). Sem isso, alguém lê "-12%" achando que é contra o mês
  passado.

- **Um intervalo muito longo busca muitos eventos** → a agregação é on-read e a janela cresce com o
  intervalo. Não há paginação no motor; um intervalo de anos pode ficar lento. Aceito por ora, com o
  cuidado de não prometer o contrário: se virar problema, aparece como lentidão, não como número
  errado.

- **Muitos pontos no gráfico com intervalo longo em frequência diária** → 180 dias em diário são 180
  pontos. O gráfico fica ilegível, mas é consequência direta da escolha do usuário e reversível
  trocando a frequência. Não vou inventar um limite silencioso que mude o que foi pedido.

- **Duas leituras do mesmo rótulo podem ser confundidas** → "Mensal" passa a significar mês de
  calendário ou 30 dias, conforme o alternador. Quem olhar um print sem o controle à vista não sabe
  qual dos dois. Mitigação: o rótulo do período diz qual está ativo ("outubro" vs "últimos 30
  dias"), e é isso que vai junto num compartilhamento de tela.

- **`Period` deixa de garantir que start é começo de balde** → o invariante que protegia contra
  datas arbitrárias some, e passa a existir código que só vale para balde. `isBucket()` deixa isso
  explícito onde importa, e os testes cobrem os dois lados de cada regra que se bifurca.

## Migration Plan

Sem migração de dados. O parâmetro de intervalo é novo e opcional: omitido, tudo se comporta como
hoje.

## Open Questions

- Deveria haver atalhos ("últimos 30 dias", "trimestre atual")? Resolvem o caso comum sem digitar
  data, mas cada atalho é uma decisão de produto sobre o que é comum — melhor decidir vendo o uso.
