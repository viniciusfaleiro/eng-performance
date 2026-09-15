## Context

Todo cálculo do motor parte de uma única `LocalDate reference`, obtida do relógio injetado em cada
método de `MetricsService` (`LocalDate reference = LocalDate.now(clock)`). A partir dela, a
`Frequency` resolve o balde (`bucketStart`), o balde anterior e a janela da série. O drilldown de
itens **já recebe** o balde explicitamente (`bucketStart`), porque precisava apontar para o mesmo
intervalo que o card mostrava.

Ou seja: o sistema já sabe calcular qualquer período. O que falta é deixar de assumir que o período
é o que contém hoje.

Restrições que moldam o desenho:

- `Frequency` já tem `bucketStart(date)`, `nextBucketStart(start)` e `elapsedDays(start, reference)`.
  Nada disso precisa mudar.
- A spec de `metrics-engine` fixa que, quando o balde corrente está em andamento, a comparação usa a
  **mesma fatia decorrida** do balde anterior. Essa regra existe para o presente e deixa de fazer
  sentido para um período encerrado.
- O painel individual usa janelas próprias por seção (calendário de 12 meses, seções no balde
  corrente, convenções em 12 meses).
- A API é consumida por uma SPA sem build; qualquer parâmetro novo precisa ser opcional para não
  quebrar o que já está no ar durante um deploy parcial.

## Goals / Non-Goals

**Goals:**

- Abrir qualquer período passado — dia, semana ou mês — com a mesma fidelidade do período corrente.
- Uma única fonte de verdade sobre "qual período está sendo visto", compartilhada por todas as telas.
- Não quebrar nenhum cliente existente: sem o parâmetro, tudo se comporta como hoje.

**Non-Goals:**

- Intervalos arbitrários, comparação de dois períodos, snapshots congelados. Ver `proposal.md`.

## Decisions

### 1. O período viaja como a data de início do balde, não como um rótulo

O parâmetro é `period=2026-07-01` — a data de início do balde — e não `"julho/2026"` nem um offset
(`-2`). Três razões:

- **Rótulo** obrigaria o servidor a interpretar texto localizado e mudaria de formato por frequência.
- **Offset** é relativo a hoje: o mesmo link abriria períodos diferentes dependendo do dia em que
  fosse aberto. Um link para "julho" precisa continuar apontando para julho amanhã.
- **Data de início** é o que o motor já usa internamente, e é exatamente o que o endpoint de
  drilldown já aceita — o vocabulário já existe no sistema.

O servidor normaliza: qualquer data dentro do balde resolve para o balde. Isso torna
`period=2026-07-15&freq=Mensal` válido e igual a julho, o que evita uma classe inteira de erro de
arredondamento no cliente.

### 2. O relógio continua sendo a origem do padrão, não some do desenho

`MetricsService` deixa de chamar `LocalDate.now(clock)` dentro de cada método e passa a receber o
período. Mas o relógio continua injetado e continua definindo o padrão quando o parâmetro é omitido,
e continua sendo o que define o que é "futuro" para a validação.

Isso preserva a propriedade que o `METRICS_REFERENCE_DATE` já dá ao ambiente de demonstração: fixar
o relógio continua fixando o "hoje" do sistema, e agora também o período padrão.

### 3. Período futuro é recusado; período sem dados não é

São coisas diferentes e merecem respostas diferentes. Um balde que ainda não começou é **erro de
pedido** — não existe resposta correta, e devolver zeros seria mentir. Já um período passado sem
nenhum evento é **resposta legítima**: zero é a informação. Confundir os dois faria o sistema
esconder um mês em que ninguém entregou nada, que é justamente o mês que alguém precisa olhar.

O balde **corrente** é aceito mesmo estando em andamento — é o padrão de hoje.

### 4. A regra de fatia decorrida vale só para o período em andamento

Hoje a comparação de um balde parcial usa a mesma fatia do balde anterior — 3 dias de setembro
contra os 3 primeiros dias de agosto. Correto para o presente; errado para o passado: julho inteiro
deve comparar contra junho inteiro.

A condição passa a ser "este balde contém o hoje do relógio", em vez de "este é o último balde".
É uma mudança de comportamento observável e por isso está na spec, não só aqui.

### 5. A janela de tendência termina no período escolhido

Escolher julho e ver uma tendência que termina em setembro seria incoerente — o card diria julho e o
gráfico diria outra coisa. A série passa a terminar no período selecionado, mantendo o mesmo número
de pontos.

Consequência aceita: ao navegar para trás, o gráfico "anda" junto. É o comportamento correto para
"estou olhando julho", e é o que torna o card e o gráfico consistentes entre si.

### 6. No painel individual, o período move as seções mas não as janelas longas

As seções ancoradas no balde corrente passam a ancorar no período escolhido. O **calendário de
contribuição de 12 meses** e a **aderência às convenções** continuam sendo janelas longas, agora
terminando no período escolhido em vez de hoje.

Encolher o calendário para um mês destruiria o que ele serve para mostrar — regularidade ao longo do
tempo. Deixá-lo terminando em setembro enquanto o resto mostra julho seria a incoerência da decisão
5. Terminar a janela longa no período escolhido preserva as duas coisas.

### 7. O estado vive na URL, e a URL é a fonte

Nó, frequência e período passam a viajar na URL. Sem isso, "olha o que aconteceu em julho" vira um
roteiro de cliques em vez de um link — e esta funcionalidade existe para embasar conversas entre
pessoas.

## Risks / Trade-offs

- **Um parâmetro novo em muitos endpoints é superfície para inconsistência** → o período é resolvido
  em um ponto só (um objeto de período construído a partir de frequência + data) e passado adiante;
  nenhum serviço recalcula balde por conta própria. Um teste afirma que card, série e drilldown do
  mesmo período cobrem exatamente o mesmo intervalo.

- **Períodos anteriores ao backfill devolvem zero, e zero parece resultado** → é o mesmo problema que
  a cobertura já tem hoje, e a mitigação é a mesma: a cobertura acompanha o período exibido, então um
  período sem ingestão aparece com cobertura zero em vez de silêncio. Vale checar na verificação que
  isso realmente se lê como "sem dados" e não como "time parado".

- **Navegar muito para trás gera consultas sobre janelas longas de eventos** → o motor já lê a
  janela inteira da série a cada requisição; o custo não muda por período ser antigo. Se virar
  problema, é problema de agregação on-read em geral, não desta mudança.

- **O usuário pode esquecer que está em um período passado e interpretar os números como atuais** →
  o controle mostra o período sempre, o atalho de voltar ao corrente fica visível, e o rótulo do
  período fora do corrente recebe destaque visual. É risco de interface, e a mitigação é interface.

## Migration Plan

Nenhuma migração de dados. O parâmetro é opcional em toda a API; um cliente antigo contra um servidor
novo continua recebendo o período corrente. Um cliente novo contra um servidor antigo é o único caso
ruim — o parâmetro seria ignorado e a tela mostraria o presente com rótulo de julho —, e é por isso
que a SPA é servida pelo mesmo jar do backend: os dois sobem juntos, sempre.

## Open Questions

- Quantos períodos a lista do rótulo deve oferecer antes de exigir outra forma de navegação? 12
  cobre um ano em mensal, mas só 12 dias em diário. Pode ser resolvido na implementação
  (12 na mensal, mais na diária) ou ficar para depois, quando alguém reclamar.
