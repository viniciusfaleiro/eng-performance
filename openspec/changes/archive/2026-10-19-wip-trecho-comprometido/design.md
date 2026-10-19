## Context

O WIP é declarado em `MetricCatalog.java:122-131` como `SUM` + `Occupation.INTERVAL`, e a pertinência
ao período é decidida em `MetricsEngine.java:381-385` por `m.spans().overlaps(from,to)`. Esses
`spans` vêm de `WorkItemFlow.fill` (`adapters/out-ado/.../WorkItemFlow.java:111`) e contêm **só os
trechos Ativo+Review** — são os mesmos intervalos que `WorkDistribution` prorrateia no painel
individual. Um único registro serve a duas perguntas diferentes, e a pergunta do WIP herdou as
fronteiras da outra: fila comprometida (Ready, Pronto p/ Prod) não está lá, e impedimento está.

A regra de folha já existe, mas só dentro do painel individual
(`WorkDistribution.java:55-58`): contêiner é quem aparece em `parentWorkItemIds()`, mais Epic por
tipo. Nenhuma outra leitura a aplica, e nenhuma tela a controla.

O classificador (`StateClassifier.java`) separa ativo / espera / terminal pela categoria do Azure
Boards, caindo em palavras-chave quando a categoria falta. Refinement e Ready são ambos espera e
nada os distingue hoje.

## Goals / Non-Goals

**Goals:**
- WIP = itens comprometidos e não entregues no período, descontado o tempo impedido.
- Um filtro de contêiner × folha que o leitor controla, com default que não muda número nenhum.
- WIP fora das frequências onde ele não pode ser lido (tudo menos Diário).

**Non-Goals:**
- Mexer nas fases do cycle time ou na classificação de "Em homolog".
- Mover o início do cycle time para o Ready.
- Reprocessar histórico automaticamente.
- Alterar a distribuição do painel individual.
- Configuração nova em Admin.

## Decisions

### D1. `Segment.QUEUE`: fila comprometida como quarto valor, que se comporta como espera

Alternativa descartada: inferir o compromisso pela ordem dos estados devolvida pelo ADO ("o último
Proposed antes do primeiro InProgress"). Funciona no board atual e quebra em silêncio em qualquer
board com dois estados de fila — e o erro seria invisível, porque o número continua plausível.

Então entra um valor novo em `Segment`, atribuído por palavra-chave no mesmo seam que já existe
(`StateClassifier`): `"ready"`, `"pronto"` → `QUEUE`; `refinement`, `new`, `backlog`, `to do` →
`WAITING`. `"ready"` sai de `WAIT_HINTS` e vai para a lista nova, checada antes.

O ponto que mantém as fases intactas: `WorkItemFlow.working(seg)` continua sendo só ACTIVE/REVIEW,
então `QUEUE` cai no mesmo ramo de `WAITING` e soma `waitMs` exatamente como antes. **As horas de
Espera, Ativo e Review não mudam em nenhum item** — e o `firstWork`, que dá início ao cycle time,
também não, porque só é marcado no ramo de trabalho.

### D2. O trecho comprometido é **contínuo**, da primeira entrada em QUEUE/ACTIVE/REVIEW ao terminal

Não é a união dos trechos comprometidos: é uma faixa só, do compromisso à entrega, com os trechos
impedidos recortados fora (`Intervals.subtract`, já no domínio). Consequência deliberada: um item
devolvido ao Refinement no meio do caminho continua dentro — ele segue sendo trabalho que o time
pegou e não entregou, e tirá-lo faria o WIP cair justamente quando um item volta atrás.

Gravado em `committed_spans`, **ao lado** de `spans`, no mesmo formato `from:to,...`. Item ainda
aberto mantém a ponta em aberto; `in_progress=1` já sinaliza isso e a extensão até o relógio da
leitura é a que `InProgressSpans` já faz.

### D3. A métrica declara qual intervalo lê

`MetricDefinition.over(Occupation.INTERVAL)` ganha a variante que recebe a chave do `detail`
(default `spans`), e o motor passa essa chave para `InProgressSpans.of(...)`. Sem isso, "qual
intervalo" ficaria implícito no único registro existente — que é exatamente como o WIP herdou as
fronteiras da distribuição individual.

### D4. Fallback explícito para o que já foi ingerido

O WIP lê o corpus inteiro (`findByType`), então, sem fallback, no dia do deploy o número despencaria
para quase zero até alguém reprocessar — e isso leria como bug, não como mudança de regra. Logo:
`committed_spans` quando existe, `spans` quando não. É semântica misturada por um período, e o texto
do "i" diz isso. Resolve-se sozinho conforme a janela avança ou no primeiro reprocessamento.

### D5. Filtro de contêiner: regra de folha promovida ao domínio

`isContainer` sai de `WorkDistribution` para o domínio, junto de um `ContainerFilter` de três
estados (ALL / CONTAINERS / LEAVES), no mesmo molde de `WorkTypeFilter` — inclusive o
`filterableBy...` que diz quando a seleção não se aplica (métrica que não é de work item). A
composição acontece onde a do tipo de trabalho já acontece (`MetricsEngine.restrict`), e o conjunto
de pais vem de `EventStorePort.parentWorkItemIds()`, que já existe e já é lido pelo painel
individual.

Default **ALL**: a leitura de hoje continua valendo até alguém trocar. É o que permite entregar o
filtro sem mudar número nenhum de surpresa — e deixa a escolha do default para depois, com o
usuário vendo os dois números lado a lado.

### D6. Esconder o WIP no servidor, não na tela

O card sai de `FlowDashboardService` e a coluna sai do heatmap quando a frequência não é diária —
os dois já recebem o `Period`, que carrega a frequência. Esconder só no JS deixaria a API
respondendo um número que nenhuma tela deveria mostrar, e qualquer consumidor novo repetiria o erro.
Na lista de Tendências, onde a escolha é estado de tela, a omissão é no cliente.

## Risks / Trade-offs

- **Semântica misturada durante a transição** (D4) → leitura de períodos antigos segue a regra
  velha; mitigado pelo texto do "i" e resolvido no reprocessamento.
- **WIP e cycle time deixam de fechar pela relação de Little** → decisão consciente do usuário;
  registrada como non-goal e dita na explicação de ambos, para ninguém tentar reconciliar.
- **`Segment.QUEUE` passa por todo `switch` existente** → o compilador acha os exaustivos, mas os
  `if` sobre `Segment` não; a varredura é obrigatória na implementação (`grep Segment\.`).
- **Palavra-chave `"pronto"`** pode casar com um estado que signifique "pronto para refinar" em
  outro board → nesse caso o item entraria no WIP cedo demais. É o preço de não hardcodar template;
  mitigação é a lista estar num lugar só e ser trivial de ajustar.
- **Filtro de contêiner em métrica que não é de work item** (deploy, PR) → tem de dizer que não se
  aplica, como o filtro de tipo de trabalho já faz, senão a seleção parece silenciosamente ignorada.
- **Cobertura JaCoCo (70/60 em domain+application)** → `ContainerFilter` e a regra de folha chegam
  ao domínio e precisam de teste próprio.

## Migration Plan

1. Deploy sem janela: nada de schema, `detail` é `jsonb` livre.
2. Sincronização seguinte passa a gravar `committed_spans`; itens antigos seguem no fallback.
3. Reprocessamento (operação manual já existente) alinha o histórico — opcional, decisão do usuário.
4. Rollback: reverter o código basta; a chave extra no `detail` fica inerte.

## Open Questions

- O default do filtro de contêiner continua `ALL`? A recomendação é revisitar depois de ver o WIP
  nas duas leituras lado a lado — "Só folhas" é provavelmente o número honesto, mas mudá-lo mexe em
  throughput, cycle time e fases de uma vez.
- Vale aplicar o filtro de contêiner também ao painel individual, que hoje força folha sempre? Fora
  do escopo desta change; hoje as duas leituras discordariam de propósito.
