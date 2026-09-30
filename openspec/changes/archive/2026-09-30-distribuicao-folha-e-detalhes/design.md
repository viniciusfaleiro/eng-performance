## Context

`IndividualDashboardService.workTypes` soma o tempo em andamento de todo work item atribuído à
pessoa. `prorate` é um sweep line que divide cada instante entre os itens ativos, então o total já é
limitado ao tempo corrido em que havia pelo menos um item aberto — **contêiner não infla total**, ele
divide cada hora por N e reduz a fatia de cada item real.

A medição em homologação (Marco, janela de 30 dias) mostrou 2.382 h de horas brutas por item, das
quais 1.768 h em uma Feature e duas User Stories. Mostrou também que excluir contêineres quase não
move os percentuais (99,996% → 99,98% de feature), porque o contêiner carrega a mesma categoria das
filhas. **Esta change corrige o total, não a pizza** — e o design assume isso em vez de prometer o
contrário.

O evento de work item hoje guarda `type` (categoria mapeada), `ado_type` (tipo cru) e
`ado_parent_type` (tipo do pai). `AdoEventSource` lê `System.Parent` para resolver o tipo do pai e
**descarta o id**. Sem esse id não há como responder "este item tem filhas?".

## Goals / Non-Goals

**Goals:**

- Que o total de horas da distribuição seja um número possível dentro da janela.
- Que "contêiner" seja decidido por evidência estrutural, não por uma lista de tipos a manter.
- Que cada figura agregada do painel individual possa ser aberta item a item, com link.
- Que a natureza das horas (corridas, divididas) esteja na tela, não só na cabeça de quem construiu.

**Non-Goals:**

- Mudar as cinco categorias, classificar por palavra-chave no título, ou subir mais de um nível na
  hierarquia para decidir o tipo. Decidido fora de escopo no pedido.
- Corrigir a herança de categoria: Task debaixo de User Story continua `feature`. É o que o board
  diz, e é por isso que a pizza não vai mudar.
- Aplicar a regra de folha a **throughput, cycle time e lead time**. Ver Riscos: é uma assimetria
  conhecida que esta change deixa de pé de propósito.

## Decisions

### 1. Contêiner é quem tem filhas, não quem tem um tipo na lista

Um work item com filhas ingeridas não contribui tempo. Um item sem filhas contribui, qualquer que
seja o tipo. Epic nunca contribui.

Alternativa considerada (a do pedido original): lista por tipo — Epic e Feature nunca, User Story só
se folha. Rejeitada por dois buracos concretos. Um **Bug com Tasks filhas** continuaria contêiner e
continuaria disputando hora com as próprias filhas. E um time que trabalha **direto em Feature sem
abrir Tasks** apareceria com distribuição vazia, o que é pior que um número distorcido: zero lido como
"não trabalhou" é exatamente o uso que a decisão de produto proíbe.

Epic fica fora por tipo (via `ado_type`) porque um Epic não é trabalho de mão nem quando está vazio —
aí é artefato de planejamento, e contá-lo seria medir a existência de um plano.

### 2. O índice de filhas é do corpus, não do período

A pergunta "este item tem filhas?" tem de ter a mesma resposta em qualquer janela. Se o índice fosse
montado com os itens do período, uma User Story cujas Tasks rodaram fora da janela viraria folha e
**voltaria a contar** — e a classificação passaria a depender de quem está olhando, o que é a pior
categoria de bug de relatório: reprodutível só para quem escolheu a mesma janela.

Então: um método novo no port que devolve o **conjunto dos ids que aparecem como pai** de algum work
item ingerido. É um `DISTINCT` sobre uma chave do detail, uma ida ao banco por request, e devolve ids
— não eventos. Carregar todos os work items para derivar o conjunto em memória seria a mesma resposta
por um preço que cresce com o histórico.

O id do próprio item já é recuperável: o evento tem id `wi:<id>`.

### 3. O pai não precisa estar acessível

A aresta é gravada **no filho**. Um pai deletado, num projeto que não ingerimos ou sem permissão não
atrapalha o teste de folha — ele só afetaria a herança de categoria, que já existe e não muda aqui.
Vale registrar porque a preocupação apareceu na análise e não se materializa.

### 4. Antes do reprocessamento, o comportamento é o de hoje

Sem `ado_parent_id`, nenhum item tem filhas conhecidas, logo tudo é folha e nada é excluído (Epic
inclusive, se `ado_type` também faltar em eventos antigos). A regra **degrada para o comportamento
atual**, não para algo pior — é a única degradação aceitável, porque um deploy sem reprocessamento não
pode produzir número novo e errado.

A consequência é operacional e vai escrita: **o reprocessamento de 6 meses faz parte do deploy**. Sem
ele a correção não tem efeito e parece deploy falhado.

### 5. "Poucos minutos" é um limite explicado, não configurável

Item com menos de **15 minutos** de tempo total em andamento é sinalizado. Não é um número descoberto
nos dados, é um julgamento: abaixo disso não existe unidade de trabalho que mereça um card, então o
card foi movido depois. Fica como constante com o raciocínio ao lado, e não como configuração — uma
configuração aqui pediria tela de admin para um número que ninguém vai querer ajustar antes de ter
evidência de que 15 está errado.

O alerta **não** exclui o item da distribuição. Ele explica a fatia perto de zero em vez de esconder o
item, que é a diferença entre um número estranho e um número enganoso.

### 6. As listas viajam no payload do painel, não em endpoints novos

Os itens da distribuição e as reviews já são calculados no mesmo request que monta o painel: o sweep
line que prorrateia as horas já sabe quanto foi de cada item. Expor em endpoint separado obrigaria a
refazer a atribuição e a checagem de escopo, e criaria uma segunda chance de divergir do número
exibido — o mesmo problema que o drilldown de métricas resolveu lendo do mesmo cálculo.

Cada lista tem **teto de 200 itens**, ordenada por horas (ou data, nas reviews) e com a contagem total
ao lado. Com o filtro de intervalo recém-entregue, alguém pode pedir seis meses, e um payload sem teto
transformaria uma escolha inocente de período numa resposta de megabytes.

### 7. Reviews recebidas listam PRs, não revisores

A lista nomeia as pull requests revisadas e a decisão de cada review. Não agrega por revisor. Somar
"Bruno rejeitou 4 das suas PRs" transformaria a visão de coaching de uma pessoa numa comparação entre
duas — e a decisão de produto é explícita: sem comparação de pessoas.

### 8. Cada item mostra as duas horas: a corrida e a contabilizada

Um item que ficou 40 h aberto pode aparecer com 2,3 h porque dividiu o tempo com outros. Mostrar só a
contabilizada faz o número parecer bug; mostrar só a corrida não explica o total. As duas juntas
tornam a conta auditável item a item: a corrida é o que o board diz, a contabilizada é o que a
métrica usou, e a diferença entre elas *é* o paralelismo — que é a informação que faltava.

Foi por comparar essas duas colunas que o caso do Marco foi diagnosticado (692 h num item dentro de
uma janela de 720 h), e é isso que a lista precisa devolver para que o próximo caso não dependa de
uma consulta ao banco.

A legenda continua necessária pelo mesmo motivo de antes: o dado não muda, a leitura muda.

## Risks / Trade-offs

- **A pizza não muda, e alguém vai esperar que mude.** A expectativa registrada no pedido era de
  distorção nos percentuais. O total cai de 2.382 h para ~614 h no caso medido; as fatias ficam onde
  estão. Mitigação: dito na proposta e aqui, para a correção não ser lida como fracasso.

- **Throughput e cycle time continuam contando contêineres.** Uma Feature concluída conta como item
  entregue ao lado das Tasks que a compõem, e o cycle time dela (semanas) entra na mediana junto com o
  das Tasks (dias). É a mesma raiz e fica fora de escopo aqui — mas passa a ser uma inconsistência
  *visível*, porque a distribuição vai excluir o que o throughput conta. Aceito por ora; vale uma
  change própria, com dados do reprocessamento na mão.

- **Um `DISTINCT` por request no painel.** Cresce com o número de work items ingeridos, não com a
  janela. É uma consulta de uma coluna e sem junção; se virar problema, aparece como lentidão do
  painel, não como número errado.

- **O limite de 15 minutos vai errar em algum caso.** Um hotfix real resolvido em 10 minutos será
  sinalizado. O alerta é uma pergunta ("esse card foi movido depois?"), não uma acusação, e o texto
  tem de sustentar isso — senão vira exatamente a vigilância que o produto recusa.

- **A hora corrida por item vai somar mais que o total.** É inevitável e é o ponto: a soma das
  corridas pode passar as horas da janela, e é justamente a comparação com a coluna contabilizada que
  mostra o paralelismo. A lista tem de dizer qual coluna soma no total, senão troca um número
  enganoso por dois.

- **Teto de 200 itens esconde o resto.** Com a contagem total ao lado, o usuário sabe que há mais; sem
  paginação, não alcança. Preferi o teto explícito a paginação que ninguém pediu.

## Migration Plan

1. Deploy da versão com `ado_parent_id` na ingestão.
2. **Reprocessar 6 meses.** Até aqui, a distribuição se comporta como hoje.
3. Conferir num caso conhecido (Marco, 30 dias) que o total caiu para a ordem de horas corridas da
   janela e que os contêineres saíram da lista.

## Open Questions

- Existe time que trabalha direto em Feature sem abrir Tasks? A regra estrutural já cobre os dois
  casos, mas a resposta muda o que esperamos ver depois do reprocessamento. `ado_type` nos dados
  responde.
