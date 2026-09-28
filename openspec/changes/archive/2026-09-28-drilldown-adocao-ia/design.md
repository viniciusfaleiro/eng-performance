## Context

`ai_adoption` é `DISTINCT_RATIO` sobre eventos `COMMIT`: o motor separa as entidades (pessoas) que
commitaram e as que têm ao menos um commit marcado, e divide uma pela outra
(`MetricsEngine.distinctRatio`). A unidade da métrica é a **pessoa**, não o commit.

O drawer, porém, chama sempre `/api/metrics/{key}/items`, que devolve os eventos crus considerados.
Para throughput ou commits isso é exatamente o certo. Para adoção, devolve commits — o insumo, não a
unidade.

O RBAC individual já existe e já é aplicado em dois lugares: o ranking de adoção (`AiDtos`, via
`canView`, que encaminha id de pessoa para `canViewIndividual`) e o heatmap (`ComparisonDtos`,
explicitamente).

## Goals / Non-Goals

**Goals:**

- Que o detalhamento da adoção responda "quem", porque é isso que a métrica mede.
- Que a lista siga o nó navegado, sem um seletor novo.
- Que nenhuma pessoa apareça para quem não pode vê-la individualmente.

**Non-Goals:**

- Generalizar o detalhamento por entidade para outras métricas; mudar o cálculo; cobrança automática.

## Decisions

### 1. Uma métrica cuja unidade é a pessoa detalha por pessoa

O drawer passa a decidir o tipo de detalhamento pela **agregação** da métrica, não por uma lista de
exceções: `DISTINCT_RATIO` conta entidades distintas, logo detalha entidades. As demais continuam
detalhando eventos.

Derivar do catálogo evita a armadilha que já nos pegou três vezes neste frontend — listas paralelas
de chaves que precisam concordar e silenciosamente divergem.

### 2. Os dois extremos vêm da mesma lista ordenada

O backend devolve **uma** lista de pessoas com seus números (commits com IA, commits totais,
proporção), ordenada. "Top usuários" e "top não-usuários" são as duas pontas dela, recortadas na
UI.

Duas consultas separadas dariam listas que podem não somar à população — e a soma é o que torna o
número do card verificável a partir do detalhamento.

### 3. "Não-usuário" é quem tem commit e nenhum com IA

O denominador da métrica é quem commitou no período. Quem não commitou não entra: não é
não-usuário de IA, é alguém que não produziu código naquele período — pode estar de férias, em
incidente, em discovery. Incluí-lo transformaria ausência em acusação.

A lista inferior é ordenada por proporção crescente, então quem tem zero aparece primeiro, e quem
usa pouco aparece em seguida — o gradiente é mais útil que um corte binário.

### 4. O escopo individual é do DTO, e reusa a função existente

Um id de pessoa passa por `AccessScope.canView`, que já encaminha para `canViewIndividual`. Foi a
lição do change de adoção por pessoa: a garantia mora no domínio, e duplicá-la na borda cria a
chance de as duas divergirem.

Consequência: na visão geral, um admin vê todo mundo; um gestor vê só seus liderados, mesmo que o
card mostre a adoção da organização inteira. O número e a lista podem discordar em tamanho, e isso
é correto — o número é agregado, a lista é nominal.

## Risks / Trade-offs

- **Uma lista de "quem não usa IA" é uma lista de pessoas associadas a algo que a organização quer
  mudar** → é o risco central, e não some com implementação. Mitigações: o escopo individual (só
  quem já podia ver aquela pessoa), o denominador honesto (só quem commitou), e o enquadramento na
  UI — a lista existe para o gestor perguntar "o que está faltando?", não para cobrar. Vale o
  produto decidir se quer rótulo neutro ("menor uso") em vez de "não usuários".

- **Baixa adoção pode ser do trabalho, não da pessoa** → quem passou o período em revisão de
  arquitetura ou incidente commita pouco e usa pouco. A lista não sabe disso. Mostrar commits com IA
  **e** commits totais dá ao leitor a chance de perceber sozinho.

- **A convenção de IA depende de disciplina** → quem usa IA e não marca o commit aparece como
  não-usuário. Já é verdade para o número do card; passa a ter nome ao lado. O painel de convenções
  do individual já alerta sobre isso.

## Migration Plan

Nenhuma: leitura sobre eventos já ingeridos.

## Open Questions

- O rótulo "não usuários" deve ser suavizado para "menor uso"? A lista é a mesma; muda como ela é
  lida, e isso importa mais aqui do que na maioria dos painéis.
