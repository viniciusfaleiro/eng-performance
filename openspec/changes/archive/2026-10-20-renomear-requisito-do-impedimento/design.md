## Context

A requirement nasceu quando o WIP lia os intervalos de trabalho, e o título registrava a decisão
daquele desenho: o sinal não mexia no WIP. O corpo foi corrigido quando a decisão mudou; o título
ficou.

## Goals / Non-Goals

**Goals:** o título dizer o que a requirement diz.

**Non-Goals:** qualquer mudança de comportamento, cenário ou código.

## Decisions

### D1. RENAMED, e não MODIFIED

O conteúdo já está certo — o que está errado é só o nome. Usar RENAMED mantém o rastro de que a
requirement é a mesma, em vez de fazer parecer que uma regra sumiu e outra nasceu no lugar.

## Risks / Trade-offs

- **Uma change só de nome parece burocracia** → é o preço de a spec ser promovida por ferramenta e
  não editada à mão; o alternativo seria editar `openspec/specs/` direto, que é justamente o que o
  repositório proíbe para não perder o histórico de por que cada regra é como é.
