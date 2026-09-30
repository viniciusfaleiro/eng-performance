## Why

A plataforma só sabe olhar baldes de calendário: um dia, uma semana, um mês. Mas as perguntas reais
raramente respeitam a borda do calendário — "como foi do início do projeto até a virada?", "e no
trimestre que fechou no dia 27?", "antes e depois da mudança de processo que entrou dia 12?".

Hoje, para responder qualquer uma delas, é preciso olhar três meses separados e somar de cabeça —
o que não funciona para mediana, razão ou qualquer coisa que não seja contagem. A resposta correta
exige recalcular sobre o intervalo inteiro, que é exatamente o que o motor sabe fazer e a navegação
não sabe pedir.

Há uma segunda forma da mesma falta, mais frequente que a primeira: hoje "Semanal" só sabe dizer
*semana de calendário* e "Mensal" só sabe dizer *mês de calendário*. No dia 3, o mês mostra três
dias — e quem quer saber como foram os últimos 30 dias não tem como pedir. As duas leituras são
legítimas e respondem perguntas diferentes: a de calendário fecha o ciclo que o time combinou, a
móvel mostra o estado recente sem depender da data de hoje.

## What Changes

- O seletor de período ganha o modo **intervalo personalizado**: duas datas, no mesmo controle que
  hoje tem as setas e a lista.
- E ganha um **alternador entre calendário e janela móvel**, para Semanal e Mensal:
  - *Calendário* — a semana ISO ou o mês corrente, como hoje (no período atual, do início até hoje).
  - *Móvel* — os últimos 7 ou 30 dias, terminando no período apontado.
  As setas continuam funcionando nos dois modos: andam um balde no calendário, e 7 ou 30 dias na
  janela móvel.
- Todas as métricas passam a ser calculáveis sobre um intervalo arbitrário, em qualquer nó.
- A comparação "vs. período anterior" passa a ser o **intervalo imediatamente anterior de mesma
  duração** — 45 dias comparam com os 45 dias anteriores.
- O gráfico de evolução **fatia o intervalo pela frequência selecionada**: a frequência deixa de
  escolher qual balde ver e passa a ser a granularidade do gráfico.
- O intervalo viaja na URL, como o período já viaja, para uma janela poder ser compartilhada.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `metrics-engine`: o intervalo de cálculo deixa de ser necessariamente um balde de frequência.
- `metrics-navigation`: a navegação passa a selecionar um intervalo arbitrário, e a frequência muda
  de papel quando ele está ativo.

## Non-goals

- **Não** remover os períodos de dia/semana/mês. Eles continuam sendo o caminho rápido, e a
  comparação com o balde anterior continua valendo neles.
- **Não** oferecer janela móvel na frequência diária: "último 1 dia" e "hoje" são a mesma coisa, e
  um alternador que não altera nada só confunde.
- **Não** inventar outras janelas móveis (14, 60, 90 dias) nesta mudança. Sete e trinta são as que
  foram pedidas; as demais viram ruído até alguém precisar.
- **Não** permitir intervalo no futuro, nem intervalo invertido (fim antes do início).
- **Não** criar comparação entre dois intervalos escolhidos à mão. A comparação continua sendo
  contra o período anterior, agora de mesma duração.
- **Não** mudar nenhum cálculo de métrica: as mesmas agregações, sobre outra janela.

## Impact

- **`domain`**: a noção de período passa a admitir um intervalo livre, mantendo a de balde.
- **`application`**: o motor passa a fatiar a série pela frequência dentro do intervalo, e a
  comparação por fatia decorrida deixa de se aplicar a um intervalo encerrado.
- **`adapter-in-web`**: os endpoints aceitam o intervalo; o controle de período ganha o modo novo.
- **Risco de leitura**: um intervalo de 45 dias comparado com os 45 anteriores é correto, mas não é
  o que a pessoa vê quando lê "vs. período anterior" pensando em mês. O rótulo precisa dizer contra
  o quê está comparando.
