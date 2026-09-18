## Context

`AiDashboardService.children(nodeId)` devolve verticais na visão geral, times dentro de uma vertical
e **lista vazia** para time ou pessoa, com o comentário `no public ranking`. O DTO já filtra os
filhos por `canView`, e o controller já recusa o nó fora de escopo.

Em paralelo, `ComparisonHeatmapService` lista **pessoas** quando o nó é um time, e o DTO as filtra
com `canViewIndividual` — regra mais estrita que `canView`, específica para pessoa.

No frontend, `snap()` consulta os caches de DORA/Fluxo/IA e, quando não encontra, chama `snapSyn()`,
que devolve zeros (o gerador aleatório foi neutralizado em julho, mas a estrutura ficou). Nenhum
chamador distingue "zero medido" de "não veio nada".

## Goals / Non-Goals

**Goals:**

- Um ranking de adoção no nível de time, com a mesma regra de acesso que o heatmap já usa.
- Que ausência de dado seja visível como ausência.

**Non-Goals:**

- Comparar pessoas entre times, ranking público de pessoas, mudança de cálculo. Ver `proposal.md`.

## Decisions

### 1. O filtro de escopo do ranking não precisa mudar

A primeira versão desta decisão dizia que o DTO teria de checar pessoa com `canViewIndividual` em
vez de `canView`. **Isso estava errado, e o teste por mutação mostrou:** afrouxar o filtro para
`canView` não quebrou nada, porque `AccessScope.canView` **já** encaminha um id de pessoa para
`canViewIndividual`. A regra de coaching mora no domínio, num lugar só.

Então o filtro existente já cobre o caso novo: ao incluir pessoas entre os filhos, elas passam
automaticamente pela regra estrita. O heatmap faz a distinção explicitamente no seu DTO, o que é
redundante — e a redundância é justamente o que cria a chance de as duas telas divergirem um dia.

O que garante o comportamento é o teste de API: um gestor com `personIds = {p:ana}` recebe apenas
Ana no ranking do time, mesmo podendo ver o time inteiro.

### 2. "Sem dados" é um estado, não um valor

`snap()` passa a marcar o retorno quando nada real foi encontrado. Quem renderiza decide como
mostrar — card com travessão, ranking com aviso —, mas ninguém mais imprime zero por falta de
resposta.

O ponto sutil: **zero medido continua sendo zero**. Um time que não fez deploy no período mostra 0,
e isso é informação. O que muda é o caso em que a API não publicou a chave: aí não há número, e
inventar um é pior que admitir a lacuna.

*Alternativa considerada:* remover `snapSyn` inteiro agora. Rejeitada nesta mudança: ele ainda
alimenta a forma das sparklines e das fases enquanto não há série real por card, e arrancá-lo junto
misturaria duas mudanças com riscos diferentes. O marcador já impede que ele produza número visível.

### 3. O nível de pessoa continua sem ranking

Estar na tela de uma pessoa e ver um ranking dos colegas é justamente a comparação que o produto
recusa. O ranking existe da vertical para baixo até o time; na pessoa, não.

## Risks / Trade-offs

- **Expor a ausência vai fazer telas hoje "completas" parecerem vazias** → é o objetivo. Vale avisar
  quem usa: um painel que vira "sem dados" não regrediu, ele parou de mentir.

- **Incluir pessoas num ranking aproxima a ferramenta da vigilância** → mitigado pelo escopo: só
  quem já pode ver aquela pessoa individualmente a recebe, e a comparação é dentro do time, que é o
  recorte que o PRD autoriza. Ainda assim é a mudança mais sensível daqui, e merece ser comunicada a
  quem lidera os times antes de virar rotina.

- **Um time grande produz um ranking longo** → o corte de TOP_N já existente vale igual.

## Migration Plan

Nenhuma. Sem esquema, sem estado.

## Open Questions

- O ranking por pessoa deveria ser ordenado por adoção (como hoje, para times) ou alfabético, para
  reduzir a leitura competitiva? Ordenar por valor é o que torna o painel útil; é também o que o
  torna um ranking.
