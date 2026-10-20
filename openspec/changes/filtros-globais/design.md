## Context

`ContainerFilter` chegou em `MetricsController` (cards, série, itens) e `FlowDashboardController`.
Os outros três use cases — individual, heatmap e IA — e o `/people` ficaram para trás. Como a SPA
manda `container=` junto de `workType=` em **todas** as chamadas, o Spring descarta o parâmetro que o
controller não declara, e o filtro some sem deixar rastro.

A distribuição do painel individual tem uma regra própria de folha (`WorkItemRole.isContainer`,
promovida ao domínio na change do WIP), decidida pela tela e não pelo leitor.

## Goals / Non-Goals

**Goals:**
- As duas seleções valerem em toda leitura de work item.
- Valor inválido virar 400 em qualquer endpoint que as aceite.
- A distribuição individual passar a responder à seleção, mantendo o padrão de hoje.

**Non-Goals:**
- Mudar o default, fazer o DORA filtrar, ou tocar em cálculo e ingestão.

## Decisions

### D1. A seleção viaja nos use cases, não só nos controllers

O mesmo padrão já usado para o tipo de trabalho: `default` no port delegando para a assinatura com a
seleção. Resolver no controller (recortando a resposta depois) daria números que não batem com a
coorte usada no cálculo — médias e razões têm de ser recalculadas sobre a população selecionada, e é
por isso que a seleção entra na população, não na saída.

### D2. A distribuição individual responde, com o padrão de hoje preservado

ALL e LEAVES contam folha — exatamente o que a tela faz hoje. CONTAINERS conta os itens que agrupam
trabalho. Deixar a distribuição imune seria coerente com o filtro de tipo (que ela ignora de
propósito, porque filtrada ela seria sempre uma fatia de 100%), mas a razão não se transfere: a
seleção de contêiner não colapsa a distribuição em uma fatia — ela troca qual conjunto de itens está
sendo dividido por tipo. A pergunta continua tendo várias respostas possíveis.

### D3. O `containersExcluded` continua sendo contado

A tela diz quantos itens saíram por serem contêiner — é o que separa "não fez trabalho folha" de
"não fez nada". Com a seleção invertida, o número passa a contar o outro lado, e a frase na tela
acompanha.

## Risks / Trade-offs

- **Mais um parâmetro em quatro assinaturas de port** → mitigado pelo `default` que mantém todos os
  chamadores atuais compilando e lendo "tudo", como antes.
- **IA e `/people` não têm métrica de work item** → a seleção não vai alterar número nenhum lá. Ainda
  assim passam a recebê-la, para que a resposta venha da regra única (`filterableByContainer`) e não
  do acaso de o parâmetro ter sido plumbado ou não.
- **A distribuição mudar de conjunto** pode surpreender quem seleciona "Só contêineres" esperando a
  lista de sempre → mitigado por a tela já dizer quantos itens ficaram de fora, agora do outro lado.
