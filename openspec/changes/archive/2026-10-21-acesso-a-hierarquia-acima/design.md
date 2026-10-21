## Context

`AccessPolicy.scopeOf` monta o escopo de uma conta a partir do cargo e da posição da pessoa:
`personIds` ganha o próprio id, `teamIds` ganha o time atual, e vertical/time extras só entram por
gestão. `AccessScope.canView` então trata `all` como privilégio de admin/exec (`orgWide`).

Medido: colaborador lê o próprio painel e o time; vertical e organização respondem 403.

As telas que comparam filhos já filtram linha a linha pelo escopo — `FlowDashboardDto` filtra o
scatter por `canView`, e `ComparisonDtos` filtra as linhas do heatmap (indivíduo por
`canViewIndividual`, o resto por `canView`). Isso é o que permite abrir um agregado sem abrir os
irmãos.

## Goals / Non-Goals

**Goals:**
- Membro enxergar time, vertical e organização.
- Lateral continuar barrado, inclusive dentro de telas comparativas.

**Non-Goals:**
- Mudar gestor, admin ou exec; abrir pessoa além da própria; configuração por organização.

## Decisions

### D1. A vertical do time entra no escopo do membro

O caminho é o mesmo que o escopo já percorre para achar o time: do `personId` para a pessoa, da
pessoa para o time, do time para `verticalId`. Nenhuma estrutura nova.

### D2. "Ver a organização" vira um direito próprio, separado de "ver qualquer nó"

`orgWide` hoje significa duas coisas de uma vez: ler o agregado da organização **e** ler qualquer
vertical ou time. Dar `orgWide` ao membro abriria o lado inteiro.

Então entra um conceito à parte no `AccessScope` — ler o agregado `all` — que o membro recebe e que
**não** implica acesso a nenhum nó nomeado. É a distinção que o produto já faz na prática entre "o
número do todo" e "o número do vizinho", e que o tipo ainda não fazia.

Alternativa descartada: expandir `teamIds`/`verticalIds` com tudo que existe e contar com o filtro
das telas comparativas. Isso transformaria a regra de produto num efeito colateral da camada de
apresentação — e bastaria um endpoint novo esquecer o filtro para o lateral vazar.

### D3. A proteção contra lateral continua onde já está, e passa a ser dita na spec

O filtro de linhas das telas comparativas já existia; o que faltava era a spec dizer que ele **é**
parte da regra, não um detalhe de tela. Sem isso, a próxima visão comparativa nasce sem ele.

## Risks / Trade-offs

- **O membro passa a ver o número da organização** → é o pedido, e é agregado: nenhuma pessoa e
  nenhum time nomeado aparecem. O que ele ganha é a régua, não o vizinho.
- **Mais um booleano no `AccessScope`** → o tipo fica com duas noções parecidas (`orgWide` e o
  direito de ler o agregado); mitigado por nomear a diferença no javadoc, porque fundi-las é
  exatamente o erro que esta change corrige.
- **Uma tela comparativa futura pode esquecer o filtro** → a spec agora exige o filtro, e o teste
  de linha do heatmap cobre o caso; não há como garantir para uma tela que ainda não existe.
