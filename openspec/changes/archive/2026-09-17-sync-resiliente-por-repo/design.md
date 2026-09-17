## Context

`AdoEventSource.fetchSince` monta a lista de eventos em duas fases: um laço por repositório (PRs,
reviews, commits) e um laço por `(organização, projeto)` (pipelines, work items). Hoje as duas
envolvem o corpo em `try/catch` que converte qualquer `RuntimeException` em
`IllegalStateException` contextualizada e **relança** — abortando a coleta inteira. O
`AdoSyncService` captura, marca o job como falho e nada é gravado.

O erro reportado (`TF401019`) vem do `HttpAdoRestClient`, que já traduz o status HTTP e preserva a
mensagem do próprio Azure DevOps. Essa parte é boa e não muda: o problema é o escopo da falha, não
sua descrição.

## Goals / Non-Goals

**Goals:**

- Que um repositório inacessível custe apenas os dados daquele repositório.
- Que quem operou a sincronização saiba exatamente o que falhou e por quê, sem ler log.
- Que a lacuna causada pela falha não se torne permanente sem ninguém perceber.

**Non-Goals:**

- Retentativa automática, remoção automática do repo, alertas. Ver `proposal.md`.

## Decisions

### 1. A ingestão devolve eventos **e** falhas, em vez de lançar

O port passa a retornar um resultado com as duas coisas. Uma coleta parcial não é exceção — é o
resultado normal de um mundo onde cadastro e realidade divergem. Modelá-la como exceção obriga o
chamador a escolher entre "tudo" e "nada", que é exatamente o comportamento que estamos removendo.

*Alternativa considerada:* manter o lançamento e acumular as falhas num efeito colateral (um
callback, como o `ProgressReporter`). Rejeitada: o resultado da sincronização é dado de retorno, e
esconder metade dele num callback torna impossível para o chamador decidir sobre o watermark.

### 2. Falha parcial **não** avança o watermark

Esta é a decisão com consequência real, e ela troca um problema por outro de propósito.

Se avançássemos, os repositórios que falharam ficariam com um buraco permanente: a janela já teria
sido "coberta" e a próxima sincronização incremental começaria depois dela. O buraco seria
silencioso — números menores, sem nada indicando por quê. Numa plataforma de medição, dado faltando
sem aviso é pior que dado atrasado.

Não avançando, a próxima execução reprocessa a mesma janela. Como `saveAll` faz upsert por id do
evento, reprocessar é idempotente: custa requisições, não corrompe nada.

O preço é que um repositório quebrado de forma permanente mantém a janela de 6 meses sendo
reprocessada a cada sincronização. É por isso que a lista de falhas precisa ser visível no resultado
e não apenas no log: ela é a ação pendente para quem opera — corrigir a permissão ou remover o repo
do cadastro.

*Alternativa considerada:* avançar o watermark apenas para os repositórios que tiveram sucesso,
guardando um watermark por repositório. É a solução correta em cheio, e é bem maior: o estado de
sincronização hoje é um registro único. Fica registrada como o caminho natural se o reprocessamento
virar incômodo.

### 3. O laço por projeto também é resiliente

O pedido falava de repositórios, mas pipelines e work items falham pela mesma razão — um projeto
renomeado, uma permissão faltando. Deixar metade do laço resiliente produziria o mesmo sintoma de
hoje por um caminho diferente. As falhas são rotuladas (`repositório X` / `projeto Y`) para que a
lista diga onde agir.

### 4. Uma falha que impede qualquer coleta continua abortando

Login não concluído e "nenhum repositório cadastrado" seguem sendo erro terminal. Não são falhas de
uma fonte entre várias: sem token não há o que coletar, e sem cadastro não há de onde. Reportá-las
como "sincronização concluída com falhas" mentiria sobre o que aconteceu.

## Risks / Trade-offs

- **Uma sincronização "concluída" pode ter ingerido metade dos dados** → é o objetivo, e por isso a
  mensagem final diz quantas fontes falharam em vez de só "concluída". Uma conclusão silenciosa com
  metade dos dados seria pior que o erro atual.

- **O watermark parado faz a janela ser reprocessada indefinidamente** → aceito conscientemente
  (decisão 2), com a lista de falhas como a ação pendente. Se o volume incomodar, o caminho é
  watermark por repositório.

- **Métricas ficam artificialmente baixas para o repo que falhou, sem que o dashboard diga isso** →
  a cobertura de atribuição não cobre este caso (ela mede evento sem pessoa, não fonte sem coleta).
  Fica como limitação conhecida: quem olha o dashboard não vê a falha, só quem olhou a
  sincronização. Resolver de verdade pediria expor o estado de coleta por repositório no dashboard —
  fora do escopo desta mudança.

## Migration Plan

Nenhuma migração. O estado de sincronização não muda de forma; muda quando ele é gravado.

## Open Questions

- Vale marcar visualmente, no dashboard, que o período exibido teve fontes com falha na coleta? Isso
  fecharia o risco acima, mas mistura estado de ingestão com apresentação de métrica.
