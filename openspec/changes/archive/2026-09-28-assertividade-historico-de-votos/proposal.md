## Why

A assertividade de PR está inflada, e de forma sistemática: um PR **rejeitado** por um revisor e
depois aprovado conta como aprovado de primeira. Um usuário com rejeição visível no histórico do PR
aparece com 100%.

A causa é o campo lido. O Azure DevOps expõe `reviewers[].vote` como **um único inteiro com o voto
atual** de cada revisor — a documentação é explícita. O fluxo normal de review sobrescreve esse
valor: o revisor vota `-10` (rejeitado), o autor corrige, o revisor vota `+10`. Quando a
sincronização roda, sempre depois do PR fechar, ela vê apenas `10`.

A métrica fica invertida: quanto melhor o revisor for em acompanhar até a aprovação, mais "assertivo"
o autor parece. Um PR rejeitado e abandonado conta pior do que um rejeitado e consertado.

O mesmo campo alimenta os eventos de review, então as **rejeições dadas** também estão subcontadas
no painel de contribuição em code review.

## What Changes

- A assertividade passa a considerar o **histórico de votos** do PR, não o voto final. Qualquer voto
  negativo em qualquer momento — rejeitado (`-10`) ou aguardando autor (`-5`) — significa que o PR
  **não** passou de primeira, mesmo que depois tenha sido aprovado.
- As rejeições dadas por um revisor passam a contar pelo mesmo critério: quem rejeitou e depois
  aprovou registra as duas decisões, não só a última.
- A ingestão passa a ler as threads do PR, onde o histórico de votos vive.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `individual-dashboard`: a assertividade passa a ser definida sobre o histórico de votos, e as
  rejeições dadas deixam de depender só do voto final.
- `ado-integration`: a ingestão de PR passa a coletar o histórico de votos.

## Non-goals

- **Não** considerar comentários não resolvidos como reprovação. Depende de disciplina de uso de
  threads que varia entre times; o voto é o sinal explícito.
- **Não** mudar o denominador: continua sendo todos os PRs da pessoa no período.
- **Não** alterar nenhuma outra métrica de PR (tamanho, tempo de review, cycle time de código).

## Impact

- **`adapter-out-ado`**: uma chamada HTTP a mais por PR (as threads); a flag `first_pass` e a decisão
  dos eventos de review passam a sair do histórico.
- **Dados já ingeridos**: ficam errados até um **reprocessamento de 6 meses**, que sobrescreve pelo
  upsert. Depois dele, a assertividade histórica **cai** e as rejeições **sobem** — os números que
  as pessoas já viram vão mudar, e isso precisa ser comunicado.
- **Custo de sync**: o laço por PR já fazia uma chamada extra (commits); passa a fazer duas.
