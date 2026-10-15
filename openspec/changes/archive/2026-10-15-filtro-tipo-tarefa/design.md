## Context

O motor agrega **on-read** sobre `raw_event`, e já sabe restringir a população de uma métrica: a
coorte com/sem IA passa um `Predicate<RawEvent>` para `MetricsEngine.series/card`. O tipo de
trabalho existe hoje só no evento **WORKITEM** (`detail.type`, cinco categorias, derivado do tipo
cru do ADO por um `switch` dentro de `AdoMapper.workType`, com a Task herdando o tipo do pai), e é
consumido por um lugar só: a distribuição do painel individual.

PR, COMMIT e REVIEW não carregam nenhum vínculo com work item — o `AdoEventSource` nunca pede os
`workItemRefs` do pull request. DEPLOY é atribuído por repositório e agrega trabalho de vários
tipos por construção.

Restrições que moldam o desenho: domínio e aplicação sem framework; o adapter do ADO é o único que
fala HTTP; nada de média de médias (o filtro tem de entrar **antes** da agregação, na população, e
não depois, sobre valores já agregados); atribuição as-of-event; e a UI é JS vanilla servida pelo
próprio serviço.

## Goals / Non-Goals

**Goals:**
- Ler qualquer métrica filtrável por uma ou mais das cinco categorias, com o filtro aplicado na
  população antes da estatística.
- Estender o filtro às métricas de IA e de código, ingerindo o vínculo PR → work item.
- Deixar explícito, na API e na tela, **o que não foi filtrado** e **o que ficou de fora por falta
  de tipo** — nenhuma exclusão silenciosa.
- Tirar o de-para de tipos de dentro do mapper e declará-lo em um lugar só.

**Non-Goals:**
- Filtrar DORA; editar o de-para por tela; criar categoria nova ou hierarquia de tipos; reclassificar
  histórico por outra regra que não o próprio de-para.

## Decisions

### D1 — O filtro é um predicado de população, não um parâmetro de agregação
Um novo valor de domínio `WorkTypeFilter` (conjunto de categorias; **vazio = tudo**) é convertido em
`Predicate<RawEvent>` e **composto** com o predicado de coorte que já existe (`and`). Entra no
mesmo ponto em que a coorte de IA entra (`MetricsEngine.match`), o que garante de graça que mediana,
razão e contagem sejam recalculadas sobre a população filtrada.

*Alternativa descartada:* filtrar depois, somando/ponderando séries por tipo. Reintroduziria média
de médias para as métricas de mediana — exatamente o que o motor foi construído para não fazer.

### D2 — O tipo é carimbado na ingestão, não resolvido na leitura
O evento PR (e os COMMIT/REVIEW daquele PR) passa a guardar as categorias dos work items vinculados
em `detail`. A alternativa — juntar PR e work item a cada leitura — exigiria carregar o corpus de
work items e refazer o join em toda consulta, e romperia a atribuição as-of-event: um item
reclassificado no board mudaria retroativamente o passado de um PR já fechado. Carimbar na ingestão
mantém o evento cru fiel ao que era quando aconteceu.

### D3 — PR com vários work items: conjunto, com casamento por interseção
`detail.work_types` guarda o conjunto ordenado das categorias dos itens vinculados (ex.:
`bug,feature`). O filtro casa se a interseção com a seleção não for vazia. Um PR que serve a um bug
e a uma feature aparece nas duas leituras — o que é verdade, e é melhor que escolher uma delas por
desempate arbitrário. Dentro de uma mesma consulta ele nunca conta duas vezes: o predicado é
booleano.

### D4 — Ausência de tipo é ausência, não `docs`
Um PR sem work item vinculado **não ganha** `work_types`. Com filtro ativo ele sai da população e é
contado em um número próprio (`untypedExcluded`), devolvido pela API e exibido na tela. Hoje o
`AdoMapper` manda todo tipo cru desconhecido para `docs`; isso continua valendo para o work item (a
distribuição não muda de forma), mas o evento passa a carregar a marca de **não mapeado**, e
Admin · Integração ADO mostra quais tipos caem ali. "Docs/Outros" deixa de ser o esconderijo.

### D5 — Filtrabilidade é derivada do tipo de evento, não um campo novo
Uma métrica é filtrável **se, e somente se**, seu `EventType` não for `DEPLOY`. As quatro métricas
DORA são as únicas de deploy, então a regra de produto ("DORA não filtra") cai fora da derivação em
vez de virar uma lista para manter em sincronia. A API devolve `filtered: true|false` por métrica;
a UI marca a coluna/card a partir disso, sem repetir a regra no JavaScript.

### D6 — De-para declarado no domínio
O `switch` sai do `AdoMapper` e vira uma tabela em `domain` (tipo cru → categoria), consultada pelo
adapter, exposta em leitura pelo Admin e usada pelo contador de tipos não mapeados. Fica no domínio
porque é regra de negócio pura e porque assim o relatório de "tipos que não mapeiam" é derivado da
própria tabela, e não de uma segunda cópia.

### D7 — Contrato HTTP
Parâmetro repetível `workType=bug&workType=feature`; ausente = sem filtro; valor fora das cinco
categorias = **400**, nunca ignorado em silêncio (um filtro ignorado devolve números certos para a
pergunta errada). A resposta ecoa o filtro aplicado e, por métrica, `filtered` e
`untypedExcluded`.

### D8 — Cobertura sob filtro
Cobertura continua medindo **atribuição**, agora sobre o denominador filtrado: eventos do(s)
tipo(s) selecionado(s) atribuídos vs. não atribuídos. Evento de outro tipo não entra como "não
coberto" — ele não faz parte da pergunta. O que ficou de fora por não ter tipo é o
`untypedExcluded`, número separado justamente para não se confundir com falta de atribuição.

### D9 — A distribuição por tipo do painel individual não é filtrada
Ela responde "como o período se dividiu", e filtrá-la devolveria sempre 100% de uma fatia. Em vez
disso, clicar em uma fatia **liga** o filtro — é o atalho natural, e o resto do painel reage.

## Risks / Trade-offs

- **Cobertura de IA cai visivelmente em time que não vincula PR a work item** → é a informação
  correta; a tela diz quantos PRs ficaram de fora (`untypedExcluded`) e o checklist de convenções
  ganha o item "PR vinculado ao work item".
- **Custo de sincronização: um pedido a mais por PR** para os `workItemRefs` → reusar o batch de
  work items que a sincronização já faz para resolver tipo/pai, e só então resolver a categoria;
  nada de buscar o work item inteiro por PR.
- **Histórico sem vínculo até o resync** → com filtro ativo, períodos antigos parecem vazios. O
  backfill de PRs faz parte desta change, e a tela avisa quando a fatia excluída por falta de tipo
  é grande demais para a leitura valer.
- **PR que casa dois tipos aparece em duas leituras** → aceito e documentado (D3); a soma das
  leituras filtradas pode passar do total não filtrado em métricas de contagem sobre PR.
- **Mais um eixo no estado global** (nó × frequência × período × tipo) multiplica as chaves de
  cache do SPA → o cache já é por chave composta; o risco é de memória, não de correção.
