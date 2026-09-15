## Context

O `MetricDefinition` já descreve uma métrica em termos estruturais: `eventType`, `aggregation`,
`measure`, `scope`, `unit`, `direction` e, quando existe benchmark, `TierBands`. O
`MetricCatalog.population(key)` guarda o filtro extra (throughput e cycle time só contam work item
**concluído**; WIP só o **em progresso**). Somando isso, o sistema já *sabe* a regra de cada
número — em forma de código.

O que falta é a tradução dessa regra para quem lê o dashboard. Hoje a única explicação é o mapa
`DEFS` no `index.html`: uma frase por métrica, no frontend, sem relação com o catálogo, e alcançável
apenas por quem descobre que o card é clicável.

O usuário decidiu por **texto curado**, não gerado do motor, com **exemplo concreto de cálculo**
junto da regra. A prioridade declarada foi clareza para quem lê.

## Goals / Non-Goals

**Goals:**

- Que qualquer número exibido tenha, a um clique, uma explicação que um gestor não-técnico entenda.
- Fonte única: a mesma explicação alimenta o ícone "i", o drawer e qualquer superfície futura.
- Impedir que uma métrica nova nasça sem explicação.

**Non-Goals:**

- Gerar texto do motor, exibir os números da consulta corrente, internacionalizar, editar pela
  Admin, ou alterar qualquer cálculo. Ver `proposal.md`.

## Decisions

### 1. A explicação é um objeto, não uma string

Uma frase corrida obriga cada autor a decidir o que contar, e o resultado fica desigual — algumas
métricas explicam a fonte, outras a estatística, nenhuma explica as duas. O modelo é:

- `rule` — a regra em uma ou duas frases, em linguagem de negócio.
- `source` — de que evento sai (commit, PR, work item, deploy) e o que é lido dele.
- `included` / `excluded` — quem entra na conta e quem fica de fora, em texto.
- `example` — um cálculo concreto com números inventados, mostrando o resultado.

O `example` é o que o usuário pediu explicitamente e é o que mais ensina: "cinco itens concluídos
levaram 10h, 20h, 30h, 40h e 50h; a mediana é 30h — não a média, que seria 30h também, mas viraria
50h se um item levasse 150h" ensina mais sobre mediana do que qualquer definição.

*Alternativa considerada:* uma string longa em Markdown. Rejeitada: sem estrutura, não há como o
teste de completude verificar que a fonte e a exclusão foram descritas, e a UI não consegue dar
hierarquia visual ao texto.

### 2. Mora no `domain`, junto da definição da métrica

`MetricDefinition` ganha a explicação. É o lugar onde as outras propriedades descritivas já
moram (`label`, `unit`, `direction`), e mantém explicação e cálculo **no mesmo arquivo** —
`MetricCatalog`. Quem muda a agregação de uma métrica vê o texto que a descreve na linha seguinte.

Isso é a principal defesa contra a divergência que o `proposal.md` admite como risco: não elimina o
problema, mas coloca os dois lados à vista um do outro. O `DEFS` do frontend falhava exatamente
por estar longe.

*Alternativa considerada:* um arquivo de mensagens separado (`explanations.properties`). Rejeitada
pelo mesmo motivo: distância entre o texto e o código que ele descreve.

### 3. Teste de completude, não de conteúdo

Um teste afirma que **toda** métrica do catálogo tem explicação com todos os campos preenchidos e
não-vazios. Isso não impede o texto de mentir sobre o cálculo — nenhum teste automatizado impede —,
mas impede o modo de falha mais provável: alguém adiciona uma métrica (como aconteceu com
`commit_count` e `pr_count` na semana passada) e esquece o texto, e o ícone "i" abre um modal vazio.

É preciso ser honesto sobre o limite: a garantia é de **existência**, não de **veracidade**. A
veracidade depende de revisão humana, e o desenho da decisão 2 é o que a torna provável.

### 4. Gráficos e painéis sem métrica usam explicações próprias

Tendência, heatmap, distribuição por tipo, cobertura e o painel individual não correspondem a uma
`MetricDefinition` — explicam uma *visualização*, não um número. Eles recebem explicações
declaradas por chave de visualização, no mesmo formato, servidas pelo mesmo endpoint.

Manter um formato só significa um componente de modal só na UI, e uma regra só de completude.

### 5. O ícone é parte do cabeçalho, não um elemento flutuante

O "i" fica ao lado do título do card/gráfico, com `aria-label` descrevendo a métrica. Ele não
substitui o clique no card (que continua abrindo o drawer com o drilldown) — são gestos distintos:
o ícone explica **como se calcula**, o card mostra **o que entrou na conta**. Confundir os dois
custaria o drilldown, que é a evidência.

### 6. O drawer consome a mesma explicação

O `DEFS` é removido do `index.html`. O drawer passa a mostrar `rule` + `source`, com o `example`
disponível, vindo do catálogo. Elimina a duplicação e garante que as duas superfícies nunca digam
coisas diferentes sobre a mesma métrica.

## Risks / Trade-offs

- **Texto curado diverge do cálculo com o tempo** → decisões 2 e 3: proximidade física no mesmo
  arquivo, e teste que barra métrica sem texto. Não há garantia automática de veracidade, e isso
  precisa estar claro para quem revisar: mudar uma agregação sem mudar o texto ao lado passa no
  build.

- **Escrever ~20 explicações é trabalho de conteúdo, não de código** → a maior parte do esforço
  deste change é redação, e redação ruim entrega um modal que ninguém lê. Vale revisar os textos
  com alguém que não conheça o código.

- **Um ícone em cada elemento polui a interface** → o "i" usa a cor de texto secundária e só ganha
  contraste no hover, seguindo o peso visual que o protótipo (`prototype/index.html`) dá a
  elementos auxiliares.

- **`MetricDefinition` cresce** → já tem 10 componentes; a explicação entra como **um** componente
  (um record aninhado), não como quatro campos soltos, para não empurrar o construtor contra o
  limite de parâmetros do Checkstyle (`ParameterNumber`, máx. 8).

## Migration Plan

Nenhuma migração de dados: a explicação é código, não estado. O único ponto de atenção é que
`MetricDefinition` é construído em vários testes; o componente novo entra com um construtor de
conveniência que o dispensa, para não reescrever fixtures que não têm nada a ver com texto.

## Open Questions

- A abrangência exata ficou como premissa, não como escolha explícita: assumi **todo elemento que
  exibe número**, seguindo o pedido original ("todo card e todo gráfico e toda informação"). Se o
  heatmap e as estatísticas do ADO puderem ficar para depois, o change encolhe bastante.
