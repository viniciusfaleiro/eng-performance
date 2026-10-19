## Context

`AdoMapper.workItem(...)` recebe `org` e `project` do chamador e monta
`org + "/" + project + "/_workitems/edit/" + id` (`AdoMapper.java:272`). Esses dois vêm do laço de
`AdoEventSource.fetchProjectActivity` (`:145-170`), que percorre os pares (org, projeto) dos
**repositórios cadastrados**. O batch GET de work items (`:266-267`) é **org-scoped**
(`org + "/_apis/wit/workitems?ids=..."`) e pede
`System.WorkItemType, System.Title, System.ChangedDate, System.CreatedDate, System.AssignedTo,
System.Parent` — o projeto do item não está entre eles, então o mapper não teria como saber mesmo
que quisesse.

Resultado observado em produção: no drill-down do Cycle Time, itens de projetos diferentes aparecem
todos com o mesmo projeto no endereço. O link abre o item certo (o ADO resolve por id na
organização), mas sob o nome errado.

## Goals / Non-Goals

**Goals:**
- O endereço de um work item não depender de qual projeto estava sendo sincronizado.
- O projeto do item virar dado do evento, e não inferência.
- Custo zero de chamadas: o dado já está a um campo de distância do que já é buscado.

**Non-Goals:**
- Descobrir por que um id de outro projeto entra na coleta (investigação separada).
- Reprocessar o histórico.
- Mexer em links de commit, PR ou build.

## Decisions

### D1. `System.TeamProject` entra no `fields=` do batch que já existe

Alternativa descartada: pedir o item individualmente para descobrir o projeto — multiplicaria o
custo da sync pelo número de work items, para um dado que o mesmo batch entrega de graça. Campo de
referência, uma string curta por item.

### D2. A URL passa a ser a forma que resolve por id

`https://dev.azure.com/{org}/_workitems/edit/{id}` abre o item em qualquer projeto da organização.
Com o projeto do item conhecido, ele pode compor o endereço; sem ele, a forma por id continua
correta. Em nenhum caso o projeto **do laço** entra no link — é precisamente a inferência que
produziu o defeito.

### D3. O projeto vira chave do `detail`

Pelo mesmo raciocínio já aplicado a `ado_type` e `ado_parent_type`: sem o dado cru no evento,
"de que projeto é este item?" não tem resposta a partir dos nossos dados, e toda revisão vira
palpite. É também o que permitirá, se a investigação sobre a WIQL for feita depois, **medir** quantos
itens de fora do projeto sincronizado estão entrando, em vez de supor.

## Risks / Trade-offs

- **Eventos já gravados mantêm o endereço antigo** → continuam abrindo o item certo; alinham-se num
  reprocessamento. Não há migração que conserte isso sem reler a fonte.
- **`System.TeamProject` ausente em algum payload** (item deletado, sem permissão, `errorPolicy`) →
  cai na forma por id, que é correta; nunca no projeto do laço.
- **Encoding**: o nome do projeto pode ter espaço ("Core Cards"). O valor vindo do item é cru e
  precisa do mesmo encode de caminho que o chamador já aplica hoje, senão o link quebra onde antes
  só estava errado.
