## Why

Todo work item concluído e não editado depois desaparecia das métricas de entrega — não como
"em andamento", mas como **sem dado nenhum de fluxo**.

O Azure DevOps marca a revisão vigente de um work item com `revisedDate = 9999-01-01`, que é o
sentinela OData para "ainda não substituída por outra" — não "sem data". Quando alguém fecha um item
e não mexe mais nele, a transição para o estado terminal **é** a revisão vigente, e portanto carrega
o sentinela. A ingestão descartava essas transições, então o item nunca ganhava data de conclusão.

Confirmado em produção em dois work items reais (WI 73079 e 73081, org ASASCFI / projeto Core Card):
`System.ChangedDate` mostra o fechamento, e o histórico de updates traz a transição
`Active → Closed` na última revisão, com `revisedDate = 9999-01-01T00:00:00Z`.

O efeito é sistemático e enviesado: itens fechados de forma limpa somem, itens fechados e depois
mexidos aparecem. O throughput subconta justamente o trabalho mais bem encerrado.

## What Changes

- Uma transição de estado na revisão vigente passa a ser datada pelo `System.ChangedDate` do próprio
  work item, em vez de descartada.
- Continua descartada quando não há `ChangedDate` utilizável, e quando o `revisedDate` é
  genuinamente ilegível — casos sem relação com o sentinela.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `ado-integration`: a reconstrução do histórico de estados passa a reconhecer a revisão vigente em
  vez de ignorá-la.

## Non-goals

- **Não** buscar a data exata da mudança de estado por uma chamada adicional à API. O
  `ChangedDate` é uma aproximação — marca a última alteração de qualquer campo —, mas no caso que
  esta correção repara, a última alteração **é** a transição de estado.
- **Não** alterar nenhum cálculo de fluxo: as mesmas fórmulas, sobre transições que antes sumiam.

## Impact

- **`adapter-out-ado`**: `WorkItemFlow` passa a receber o `ChangedDate` do work item.
- **Dados já ingeridos**: os itens afetados estão gravados sem `completed`. Um **reprocessamento de
  6 meses** os corrige pelo upsert. Depois disso, **throughput, cycle time, lead time e flow
  efficiency históricos vão mudar** — o throughput sobe, porque entregas que estavam invisíveis
  passam a contar.
