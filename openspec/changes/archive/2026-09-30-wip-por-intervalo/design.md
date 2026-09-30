## Context

`MetricsEngine.inBucket` (linha 336) decide, para toda métrica, se um evento pertence a um período:
`bucket.contains(m.date())`. É o **único** ponto onde isso é decidido, e as três leituras passam por
ele — o valor do card (`selectedValue`), cada ponto do gráfico (`series`) e a lista do drill-down
(`items`). Isso é sorte: significa que a mudança de conceito do WIP tem um lugar só para acontecer, e
que a lista não pode divergir do número por construção.

O dado necessário já existe. `WorkItemFlow` grava em todo work item a chave `spans` — os intervalos
ACTIVE+REVIEW reconstruídos do histórico de estados, no formato `"início:fim,início:fim"` em millis
(`WorkItemFlow.java:74`). É o mesmo dado que a distribuição de horas do painel individual prorrateia.
**Nenhum reprocessamento é necessário** para esta change.

Duas descobertas da auditoria do catálogo entram aqui porque têm a mesma raiz — coisa em andamento
medida como se estivesse pronta:

- Flow Efficiency conta item sem dado como 0%: sem `num`/`den` na ingestão, o motor usa os defaults
  `num=0`/`den=1` (`MetricsEngine.java:293-294`) e a razão `soma(num)÷soma(den)` afunda. A spec de
  `flow-dashboard` já proibia isso; o código divergia.
- Ativo, Espera e Review incluem item inacabado com medida parcial, porque `POPULATIONS`
  (`MetricCatalog.java:377`) só filtra throughput, cycle time, flow lead time e WIP.

## Goals / Non-Goals

**Goals:**

- Que WIP responda "quantos itens estiveram em progresso neste período", incluindo o item parado.
- Que a mesma pergunta ("que intervalos este item ficou em progresso?") tenha uma implementação só.
- Que a explicação na tela descreva a conta com precisão suficiente para a próxima divergência ser
  detectável por leitura.
- Que as fases voltem a descrever a mesma população que o Cycle Time ao lado.

**Non-Goals:**

- WIP médio simultâneo (o WIP da Lei de Little). Sai dos mesmos spans, mas "3,2 itens em média" é
  difícil de acionar e a média esconde os itens que se quer nomear. Se a matemática de Little for
  necessária, entra como métrica separada.
- Filtro de conclusão em `pr_review_time`, `code_cycle_time` e `code_throughput`. Hoje corretas por
  a ingestão pedir `status=completed` ao ADO; é seguro contra cenário futuro, não correção de hoje.
- Guardar os limites do intervalo em colunas indexadas. Ver Riscos.

## Decisions

### 1. A forma de ocupar o tempo é declarada no catálogo, não deduzida

`MetricDefinition` ganha como um evento ocupa o tempo: **instante** (o padrão de hoje) ou
**intervalo**. `inBucket` passa a ramificar nisso.

Alternativa rejeitada: tornar o filtro de população ciente do período, trocando
`Predicate<RawEvent>` por algo que receba o bucket. Mudaria a assinatura compartilhada por todas as
métricas para servir a uma, e o filtro de população existe para outra coisa — recortar coorte por
atributo do evento (o flag de IA). Misturar as duas responsabilidades faria a próxima pessoa não
saber onde procurar.

Também rejeitado: uma agregação nova (`OVERLAP_COUNT`). A agregação responde "que estatística", e a
estatística do WIP continua sendo contagem. O que muda é **quem entra na conta**, e isso é
pertencimento ao período.

### 2. O intervalo é lido dos spans, com o teste exato — não dos limites

O teste é "algum span cruza [início, fim)", span por span. Usar só o primeiro começo e o último fim
seria mais barato e **errado**: uma tarefa trabalhada de 1 a 3 e retomada de 20 a 22 tem um buraco no
meio, e um período de 10 a 15 cairia dentro do buraco contando um item que ninguém tocou.

### 3. O item aberto vai até o relógio da leitura

O span de um item sem conclusão é fechado com o `now` da **ingestão**. Para a leitura diária
funcionar, ele tem de ir até o `now` da **leitura** — o mesmo `Clock` injetado que o resto do motor
usa, então o ambiente de demonstração continua determinístico.

O flag `in_progress` passa a ter exatamente esse trabalho: dizer que o último span é aberto. Sem ele
não há como distinguir "terminou às 18h" de "estava rodando quando sincronizamos às 18h".

**O que isso assume, e pode errar:** que o item ainda está aberto agora. Se o sync foi há três dias e
a tarefa foi concluída anteontem, o WIP de hoje a conta. É desatualização, não erro de conta, e a
alternativa é pior — confiar no relógio da ingestão derruba o WIP diário inteiro sempre que o
sincronizador não roda de manhã. Argumenta a favor de mostrar na tela quando foi o último sync.

### 4. Um tipo só responde "que intervalos este item ficou em progresso?"

A distribuição de horas parseia `spans` inline hoje. Com o motor precisando da mesma coisa, seriam
duas implementações do mesmo formato — e a segunda nasceria já divergindo na decisão 3, porque a
distribuição para no relógio da ingestão. Então o parse, o recorte e o teste de sobreposição passam a
viver num tipo só, usado pelos dois.

Consequência assumida: **as horas da distribuição de um item aberto mudam**, passando a ir até a
leitura. É o mesmo defeito da decisão 3 e a mesma correção; deixar os dois lados discordando sobre
até quando um item está em progresso, na mesma tela, não se sustenta.

### 5. WIP lê o corpus, não uma janela de datas

`MetricsService.fetch` é chamado **uma vez por métrica** (linha 68), não por tipo de evento, então
dar ao WIP uma busca diferente não mexe no caminho das outras.

Alargar a janela de datas foi considerado e rejeitado: para pegar o item que concluiu em agosto ao
ler junho, a janela teria de ir de junho até hoje; para pegar o item aberto há dois anos, voltar dois
anos. Alargar "o suficiente" degenera em ler tudo, com a diferença de continuar errado nas pontas. Se
o destino é ler tudo, melhor ler tudo de propósito e com o teste certo.

### 6. As fases e a eficiência passam a contar só item concluído

Quatro entradas novas em `POPULATIONS`: `flow_efficiency`, `active_time`, `waiting_time`,
`review_time`, todas com o filtro de conclusão que throughput e cycle time já usam.

Isso resolve as duas descobertas de uma vez: item sem histórico aproveitável não recebe `completed`,
então sai por consequência e para de entrar na razão como 0/1. E as fases voltam a descrever a mesma
população do Cycle Time exibido ao lado.

O que se perde: as horas de fase de um item ainda aberto não aparecem em lugar nenhum. É aceitável —
"o que está em progresso" é a pergunta do WIP, e "quantas horas" é a da distribuição.

### 7. A explicação passa a dizer onde o item cai no tempo

Toda explicação ganha a frase que falta em todas: **qual data ou intervalo coloca o item no período
selecionado**. É exatamente onde o texto e o código do WIP divergiram — *"itens que estiveram em
andamento em algum momento do período"* descreve tanto a conta certa quanto a errada, e não dá para
saber qual olhando a frase.

Onde a atribuição é convenção e não conta, a convenção vai escrita: o Cycle Time de um item que
levou três meses é atribuído **inteiro** ao período em que ele concluiu.

## Risks / Trade-offs

- **WIP deixa de ser comparável entre períodos de tamanhos diferentes.** Um mês sobrepõe mais itens
  que um dia, e a direção da métrica é "menor é melhor", então uma janela longa sempre parece pior.
  Com o filtro de intervalo já entregue, alguém pode pedir seis meses e ver um número enorme marcado
  como ruim. A comparação contra o período anterior de igual duração continua honesta. Mitigação no
  texto da tela, não na conta — recortar a conta responderia outra pergunta.

- **WIP passa a ler todos os work items por requisição.** Cresce com o corpus, não com a janela.
  Guardar o primeiro e o último instante do intervalo em campos próprios e indexados permitiria o
  banco pré-filtrar, com o teste exato em memória continuando por cima — é o melhor estado final e
  **não muda nenhum número**. Fica fora agora por ser otimização sem lentidão medida. Gatilho
  explícito: quando a requisição do painel de Fluxo passar de 2 s, ou quando o corpus passar de
  ~50 mil work items.

- **Flow Efficiency vai subir, possivelmente muito**, em qualquer time com itens sem histórico de
  estado — eles estavam sendo contados como 0%. O número novo é o certo; o susto é real e vale ser
  anunciado antes de alguém ver o gráfico mexer.

- **As horas da distribuição de itens abertos mudam** (decisão 4), inclusive num caso que já foi
  conferido em homologação. É correção, mas muda um número já visto.

- **O teste de sobreposição roda por item e por bucket.** O gráfico desenha 12 buckets, então são 12
  testes por item. É comparação de inteiros sobre uma lista curta de spans; se pesar, aparece como
  lentidão, não como número errado.

## Migration Plan

Não há. `spans` já está gravado, então o comportamento novo vale para o histórico ingerido no
deploy. Conferir em homologação, na ordem: (1) uma tarefa aberta e intocada há semanas aparece no WIP
de hoje; (2) o WIP de um mês fechado não muda ao ser lido duas vezes; (3) a Flow Efficiency subiu e a
cobertura explica por quê; (4) Ativo+Espera+Review passaram a descrever a mesma população do Cycle
Time.

## Open Questions

- **A atribuição de uma métrica de intervalo continua sendo pela data do registro.** A regra do
  produto é as-of-event: o período fica com o time a que a pessoa pertencia na hora do evento. Para
  um item em progresso, "a hora do evento" é a última alteração, que não é nem o começo nem o fim do
  trabalho — então um item que atravessou uma troca de time atribui pela data em que o board foi
  mexido por último. Apareceu montando os testes, não é regressão (é a regra que já valia), e uma
  correção precisaria decidir o que "as-of" significa para um intervalo: o começo do trabalho, o fim,
  ou cada período pelo time daquele momento. Nenhuma das três é obviamente certa, e a terceira muda
  o modelo de atribuição inteiro.
- A tela mostra quando foi a última sincronização? A decisão 3 faz o WIP diário depender disso, e se
  não mostra, vale uma change própria — o usuário não tem como saber que está lendo dado de ontem.
