## Why

Hoje um colaborador vê **o próprio painel e o agregado do time**, e nada mais. Medido com a conta
`bruno.lima@empresa.com` (Colaborador, time Checkout): próprio painel 200, time 200, **vertical 403,
organização 403**, outro time 403, outro dev 403.

Isso esconde dele justamente o contexto que dá sentido ao próprio número. Um cycle time de 40 h não
diz nada sozinho; diz tudo quando se vê que o time está em 38 h e a organização em 20 h. Sem a
hierarquia acima, o colaborador só pode comparar-se consigo mesmo — e a decisão de produto é medir
para melhorar o sistema, não para cada um olhar o próprio umbigo.

O que **não** muda é o que o produto recusa: comparação lateral. Par, outro time e outra vertical
continuam fora.

## What Changes

- **O colaborador passa a ver toda a hierarquia acima dele**: o time, a vertical do time e o
  agregado da organização.
- **A lateralidade continua barrada**: outro time, outra vertical e o painel individual de qualquer
  outra pessoa seguem 403.
- As telas que comparam filhos (heatmap, scatter) continuam filtrando linha por linha pelo escopo,
  então subir um nível **não** abre os irmãos: no agregado da organização o colaborador vê o número
  da organização, e entre as linhas comparadas, só o que ele já podia ver.
- Gestores e admin/exec não mudam.

## Capabilities

### Modified Capabilities
- `authorization`: o escopo de um membro passa a incluir a cadeia de agregados acima dele, e a regra
  de negação passa a ser dita em termos de lateralidade, não de nível.

## Non-goals

- **Não abrir pessoa nenhuma além da própria**: a visão individual continua coaching, do próprio
  dono e do gestor dele.
- **Não abrir time ou vertical irmãos**, nem por aparecerem numa comparação.
- **Não mexer em admin, exec ou gestores.**
- **Não criar configuração**: é regra de produto, não opção por organização.

## Impact

- **domain**: `AccessPolicy` passa a incluir a vertical do time do membro, e `AccessScope` ganha a
  noção de "pode ler o agregado da organização" separada de "pode ler qualquer nó".
- Nenhuma mudança em ingestão, persistência, cálculo de métrica ou contrato REST.
