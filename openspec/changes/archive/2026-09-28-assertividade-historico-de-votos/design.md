## Context

`AdoMapper.firstPass(pr)` percorre `pr.reviewers` e devolve falso se algum `vote < 0`. `AdoMapper
.reviews(pr)` usa o mesmo array para emitir um evento `REVIEW` por revisor, com `decision` derivada
do mesmo número.

O campo é documentado como *"Vote on a pull request: 10 approved, 5 approved with suggestions, 0 no
vote, -5 waiting for author, -10 rejected"* — um valor, o corrente. O próprio modelo tem `isReapprove`
("indicates if this approve vote should still be handled even though vote didn't change"), o que
confirma que revotar é o caminho normal.

O histórico está nas **threads** do PR: `GET .../pullRequests/{id}/threads` devolve threads de
sistema com `properties.CodeReviewThreadType = "VoteUpdate"` e o valor em
`properties.CodeReviewVoteResult`.

## Goals / Non-Goals

**Goals:**

- Que "passou de primeira" signifique o que diz: ninguém pediu mudança em momento algum.
- Que a rejeição de um revisor não desapareça porque ele aprovou depois.

**Non-Goals:**

- Comentários como sinal de reprovação, mudança de denominador, outras métricas de PR.

## Decisions

### 1. O sinal é o voto negativo em qualquer momento, não o voto final

Decisão do usuário, e é a leitura literal da métrica. `-5` ("aguardando autor") conta junto com
`-10` ("rejeitado"): os dois significam que o PR voltou para o autor, que é exatamente o que
"não passou de primeira" descreve. Distinguir os dois faria a métrica depender de qual botão cada
revisor prefere apertar para a mesma intenção.

### 2. O histórico vem das threads, e a ausência dele não inventa aprovação

Se as threads não puderem ser lidas — permissão, erro, PR antigo demais —, o PR **não** é marcado
como first-pass. Um PR sobre o qual não se sabe nada não é um PR exemplar.

Isso é o oposto do comportamento atual, que na dúvida marcava first-pass. Preferir o lado
pessimista é o que impede o número de voltar a subir sozinho quando a coleta falha — e falha de
coleta agora é visível, porque o sync reporta as fontes que falharam.

### 3. Os eventos de review passam a refletir as duas decisões

Um revisor que rejeitou e depois aprovou tem as duas coisas no histórico. Emitir só a última
esconderia o trabalho de review que de fato aconteceu — e é o trabalho de review que o painel de
contribuição existe para mostrar.

O id do evento hoje é `review:{prId}:{identity}`, um por revisor por PR. Manter esse id e marcar a
decisão como `changes_requested` quando houve **qualquer** voto negativo preserva a cardinalidade (um
evento por revisor por PR) e captura o fato relevante. Emitir um evento por voto mudaria a contagem
de "reviews dados", que hoje significa "PRs que revisei", não "vezes que votei".

*Alternativa considerada:* um evento por voto, com id incluindo o timestamp. Rejeitada: inflaria
"reviews dados" de um jeito que ninguém pediu, e a pergunta que o painel responde é sobre PRs.

### 4. Uma chamada a mais por PR, não por voto

As threads vêm em uma requisição por PR. O laço já fazia uma (commits); passa a duas. Para um
backfill de 6 meses isso dobra o custo do trecho de PRs — aceitável, e mensurável pelo log de
progresso que já existe.

## Risks / Trade-offs

- **Os números históricos vão mudar depois do reprocessamento** → assertividade cai, rejeições
  sobem. É a correção, não uma regressão, mas alguém que anotou o número anterior vai estranhar.
  Precisa ser dito antes, não descoberto depois.

- **Times que usam "aguardando autor" como lembrete leve vão ver a assertividade cair mais** →
  consequência aceita da decisão 1. Se incomodar, a alternativa registrada é contar só `-10`.

- **Um PR sem threads legíveis deixa de ser first-pass** → conservador de propósito (decisão 2).
  Se um repositório inteiro falhar na leitura de threads, a assertividade daquele time despenca —
  por isso a falha precisa aparecer na lista de fontes com erro, e não ser silenciosa.

## Migration Plan

Nada de esquema. Depois do deploy, **reprocessar 6 meses** para sobrescrever a flag dos PRs já
ingeridos (upsert por id do evento).

## Open Questions

- Vale distinguir na UI "sem voto nenhum" de "aprovado de primeira"? Hoje um PR que ninguém revisou
  já não conta como first-pass, mas também não é sinalizado como não-revisado em lugar nenhum.
