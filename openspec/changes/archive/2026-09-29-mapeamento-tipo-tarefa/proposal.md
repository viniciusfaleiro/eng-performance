## Why

A classificação de esforço do card "Distribuição do Trabalho" nasceu como **placeholder**, e ninguém
registrou isso. `AdoMapper.workType()` mapeia `Task → manutenção` e `Epic → dívida técnica` — duas
regras sem justificativa no PRD, no protótipo ou em qualquer spec. O resultado é um gráfico que
parece medido e é arbitrário.

O efeito é grande porque `Task` é o tipo mais numeroso na maioria dos boards: praticamente todo o
esforço do time cai em "Manutenção", e o gráfico passa a dizer que o time só faz manutenção. Já
`Epic`, que é o agrupador de iniciativa por excelência, entra como dívida técnica.

O time já criou dois tipos customizados no Azure DevOps — "Tech Debt" e "Documentation or Other" —
que o mapeamento ignora por completo, tratando ambos pelo default.

## What Changes

O mapeamento de `System.WorkItemType` para categoria passa a ser:

| Tipo no Azure DevOps | Categoria | |
|---|---|---|
| Bug | bug | mantém |
| User Story, Feature, Product Backlog Item | feature | mantém |
| Epic | **feature** | muda (era dívida técnica) |
| Tech Debt *(customizado)* | **dívida técnica** | novo |
| Documentation or Other *(customizado)* | **docs/outros** | novo, explícito |
| Task | **herda a categoria do work item pai** | muda (era manutenção) |
| Task sem pai resolvível, ou cujo pai também é Task | docs/outros | limite de um nível |
| Qualquer outro tipo | docs/outros | mantém |

- A ingestão passa a pedir o pai (`System.Parent`) junto dos demais campos, e a resolver os pais que
  não vieram no mesmo lote em **uma chamada por página de 200 ids**, nunca uma por Task.
- A tabela passa a existir numa spec — hoje ela só existe dentro de um `switch`.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `ado-integration`: o tipo de trabalho passa a ser mapeado por uma regra especificada, incluindo a
  herança da Task para o pai.

## Non-goals

- **Não** mudar as cinco categorias que o painel consome. Ver o risco sobre "Manutenção" no
  `design.md`: com esta tabela, nenhum tipo mapeia para ela.
- **Não** subir mais de um nível na hierarquia. Uma Task cujo pai é outra Task cai no default, em
  vez de buscar o avô — decisão deliberada para o custo de chamadas ficar previsível.
- **Não** tornar a tabela configurável pela tela de Admin.

## Impact

- **`adapter-out-ado`**: `workType` passa a receber o tipo do pai; `fetchWorkItems` ganha três fases
  (coleta, resolução dos pais faltantes, montagem); nova classe para a resolução em lote.
- **Dados já ingeridos**: mantêm a classificação antiga até um **reprocessamento de 6 meses**.
  Depois dele, a distribuição de trabalho muda bastante — "Manutenção" esvazia e "Feature" cresce.
