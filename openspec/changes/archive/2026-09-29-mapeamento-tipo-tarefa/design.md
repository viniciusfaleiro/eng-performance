## Context

`AdoMapper.workType(String)` é um `switch` sobre `System.WorkItemType` em minúsculas, com `default →
docs`. `fetchWorkItems` pede hoje `WorkItemType, Title, ChangedDate, CreatedDate, AssignedTo` num GET
em lote de até 200 ids, e para cada item faz mais uma chamada para o histórico de updates.

As cinco categorias que o painel consome (`IndividualDashboardService.WORK_TYPES`) não mudam.

## Goals / Non-Goals

**Goals:**

- Que a categoria reflita a natureza do trabalho, não um placeholder.
- Que Task — o tipo mais numeroso — herde o sentido de quem ela serve.
- Que a regra fique escrita numa spec, não só num `switch`.

**Non-Goals:**

- Mudar as categorias, subir mais de um nível, tornar a tabela configurável.

## Decisions

### 1. Task herda do pai, com limite de um nível

Uma Task não tem natureza própria: "escrever o teste" é feature se pende de uma User Story e dívida
técnica se pende de um item de Tech Debt. Classificá-la por si mesma é o que produz o placeholder
atual.

O limite de um nível é sobre custo previsível. Subir a hierarquia até achar um tipo conclusivo
tornaria o número de chamadas função da profundidade do board — que varia por time e que ninguém
controla. Um nível resolve o caso dominante (Task pendurada direto no item de trabalho) com teto
fixo. Task de Task cai no default, e isso é uma escolha visível, não um esquecimento.

### 2. Os pais são resolvidos em lote, não por Task

Três fases em `fetchWorkItems`:

1. **Coleta** — acumula os itens de todas as páginas e um índice `id → tipo`. Isso já resolve de
   graça o caso em que o pai também mudou na janela e veio no mesmo lote.
2. **Resolução** — os pais ausentes do índice viram um `Set` (dedup), paginado de 200 em 200, num GET
   que pede só `System.WorkItemType`.
3. **Montagem** — o laço de mapeamento, agora com o tipo do pai em mãos.

Uma chamada por Task seria o caminho ingênuo e multiplicaria o custo de sync pelo número de tasks —
que é o tipo mais numeroso. O dedup importa porque várias Tasks costumam pender do mesmo pai.

### 3. Falha ao resolver o pai degrada, não interrompe

A resolução em lote captura a exceção, loga em WARN e segue como "nada resolvido" — as Tasks
afetadas caem no default. É o mesmo tratamento que `stateClassifier` já dá, e é coerente com a
sincronização resiliente por fonte: um erro num campo auxiliar não deve custar a ingestão.

### 4. `System.Parent` é campo de referência, não `relations`

O campo traz só o id do pai, sem inflar o payload. `relations` traria o grafo inteiro de links de
cada item — muito mais dado para responder uma pergunta de um inteiro.

Self-parent (`Parent == id`) é tratado como não resolvido: é dado inconsistente, e segui-lo daria
recursão.

## Risks / Trade-offs

- **Nenhum tipo mapeia mais para "Manutenção"** → a categoria continua nas cinco do painel e passará
  a aparecer sempre em 0%. É um zero medido, não inventado, e a categoria fica pronta caso o time
  crie um tipo "Maintenance" no ADO. Mas vale a decisão de produto: ou algum tipo deveria cair ali,
  ou a fatia deveria sair do gráfico. Fica registrado porque é consequência direta desta tabela e
  não estava no pedido.

- **A distribuição histórica vai mudar muito no reprocessamento** → "Manutenção" esvazia e "Feature"
  cresce. Quem comparar um print antigo vai achar que quebrou. É a correção do placeholder, mas
  precisa ser dito antes.

- **Uma chamada extra por página de pais faltantes** → teto baixo e previsível; no melhor caso
  (pais no mesmo lote) é zero.

- **Tipos customizados são casados por nome** → se o time renomear "Tech Debt" no ADO, a
  classificação silenciosamente volta ao default. É o mesmo acoplamento que a regra já tem para os
  tipos padrão, e a alternativa (configurar na Admin) está fora de escopo.

## Migration Plan

Sem migração. Depois do deploy, **reprocessar 6 meses** para reclassificar o que já foi ingerido.

## Open Questions

- "Manutenção" deve receber algum tipo, ou sair das cinco categorias?
