## Why

No drill-down do Cycle Time, os work items aparecem com o **projeto errado** no endereço: tarefas de
um projeto são listadas com a URL de outro. O link ainda abre o item certo — o Azure DevOps resolve
work item por id na organização inteira —, mas quem lê vê um projeto que não é o do item, e a
primeira reação é duvidar da lista inteira. O drill-down existe exatamente para o time **conferir**
se acredita no número; um endereço que parece errado destrói o que a tela foi feita para construir.

A causa é estrutural: a URL é montada com o projeto do **laço da sincronização**
(`AdoMapper.java:272`, alimentado por `AdoEventSource.fetchProjectActivity`), e não com o projeto do
item. O item nunca é perguntado a respeito — `System.TeamProject` não está entre os campos pedidos
no batch GET, que por sua vez é **org-scoped** e devolve qualquer id da organização sem reclamar.
Se um id que não é daquele projeto entra na coleta, o evento é gravado com o projeto da vez; se o
mesmo item é coletado em mais de uma iteração, sobra o projeto da última.

## What Changes

- **O projeto passa a vir do próprio work item.** `System.TeamProject` entra no `fields=` do batch
  GET que já é feito — campo de referência, sem chamada nova e sem custo relevante de payload.
- **O link do work item deixa de depender do projeto para ser correto**: passa a ser a forma que o
  Azure DevOps resolve por id na organização (`…/_workitems/edit/{id}`), com o projeto usado apenas
  quando ele é conhecido pelo próprio item. Projeto na URL é decoração que só serve para errar.
- **O evento passa a registrar a que projeto o item pertence**, de modo que "este item é de outro
  projeto" vire uma pergunta respondível pelos dados, em vez de um palpite a partir do laço.
- Correção vale para o que for **sincronizado depois**; eventos já gravados mantêm o endereço antigo
  até um reprocessamento — e continuam abrindo o item certo nesse meio tempo.

## Capabilities

### Modified Capabilities
- `ado-integration`: o endereço e o projeto de um work item passam a ser lidos do próprio item, não
  inferidos do contexto da sincronização.

## Non-goals

- **Não investigar aqui por que um id de outro projeto chega à coleta.** A WIQL é postada no
  endpoint com projeto e deveria escopar sozinha; se não estiver escapando, isso é outra
  investigação. Esta change faz a questão deixar de produzir link errado, de qualquer forma.
- **Não reprocessar o histórico automaticamente.**
- **Não mexer em links de commit, pull request ou build**, que já vêm prontos da própria API.
- **Não criar tela nem filtro por projeto.**

## Impact

- **adapter-out-ado**: um campo a mais no `fields=` do batch GET de work items; a montagem da URL em
  `AdoMapper.workItem`; uma chave nova no `detail` com o projeto do item.
- Sem migração, sem índice, sem mudança de contrato REST: o drill-down continua lendo
  `detail["url"]`.
