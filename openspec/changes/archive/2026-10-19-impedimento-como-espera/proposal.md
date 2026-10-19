## Why

O board do time não tem coluna "WAITING": impedimento é sinalizado pelo campo `Impediment`
do work item e/ou por uma tag `Impedimento:<motivo>`. Como as fases do cycle time hoje saem
exclusivamente da categoria do estado do Azure Boards, um item parado dias dentro da coluna
"Ativo" conta como 100% trabalho — o **Flow Efficiency** aparece inflado e a **quebra do
cycle time** (Espera/Ativo/Review) mente na mesma proporção. Medir para melhorar o sistema
(PRD, `docs/initial-spec.md`) depende justamente de ver onde o trabalho **para**: hoje o
tempo bloqueado é invisível, e com ele a maior alavanca de melhoria de fluxo do time.

## What Changes

- **Convenção de Impedimento configurável em Admin** (nome do campo + prefixo da tag), no mesmo
  padrão da Convenção de IA. Vazia = overlay desligado.
- **Overlay na reconstrução de fluxo do work item**: os intervalos em que o item estava
  marcado como impedido deixam de contar como Ativo/Review e passam a contar como Espera.
  A classificação por estado continua sendo a base — o impedimento é sobreposição, não
  substituição. Isso corrige **de uma vez** o Flow Efficiency e as três fases do cycle time,
  que leem as mesmas horas.
- **Quebra da Espera por tipo de impedimento**: o motivo é o que vem depois de
  `Impedimento:` (`Impedimento:infra` → `infra`); intervalo marcado só pelo campo entra em
  "Sem motivo"; o tempo de espera sem nenhum sinal aparece como "Fila do board", de modo que as
  partes **sempre somam** as horas de espera da população do período. Elas compõem esse total — e
  não o card de Espera, que é mediana: mediana não é soma de nada, e apresentar fatias como se
  fossem uma divisão dele inventaria uma conta que não fecha. O total que elas compõem é exibido
  junto da quebra.
- **Segmento "Espera" clicável** no card de fases, abrindo a quebra por motivo junto da lista
  de itens considerados no cálculo.
- **Painel individual passa a descontar** o tempo impedido do trabalho do liderado — sem
  alterar os intervalos que alimentam WIP/ocupação: um item bloqueado continua em andamento.
- **Botões "i" atualizados** para descrever as regras novas (fases, Espera, Ativo, Review,
  Flow Efficiency, quebra por motivo, distribuição individual e a convenção em Admin).
  **BREAKING (leitura)**: nenhuma API muda de forma incompatível, mas o significado histórico
  de Ativo/Review/Espera/Flow Efficiency muda para quem usa o sinal — números de períodos já
  sincronizados só mudam após reprocessar os eventos.

## Capabilities

### New Capabilities
- `impediment-signal`: como o sistema reconhece tempo impedido no Azure DevOps (campo e/ou
  tag configuráveis), como esse tempo é transferido de trabalho para espera na reconstrução
  de fluxo do work item, e como o motivo é extraído e atribuído ao tempo.

### Modified Capabilities
- `flow-dashboard`: a Espera do card de fases ganha quebra por tipo de impedimento, com
  residual explícito de fila, e o segmento passa a ser acionável.
- `metric-drilldown`: a Espera entra no conjunto de métricas com drill-down, e seu painel
  exibe a quebra por motivo acima da lista de itens.
- `platform-config`: nova convenção configurável (campo + prefixo de tag) com default
  semeado e desligamento por valor vazio.
- `individual-dashboard`: tempo impedido deixa de contar como trabalho na distribuição do
  liderado.
- `metric-explanation`: explicações das fases, das três métricas de fase, do Flow Efficiency,
  da distribuição individual e uma explicação nova para a quebra por motivo.

## Non-goals

- **Nenhuma métrica nova no catálogo** — sem "nº de impedimentos por item", sem "WIP bloqueado
  agora", sem card novo. O sinal se funde na Espera que já existe.
- **Não alterar WIP/ocupação**: um item bloqueado continua em progresso; tirá-lo do WIP seria
  outra decisão de produto.
- **Não mexer na classificação de estados** (`StateClassifier`) nem introduzir uma quarta fase.
- **Sem reprocessamento automático** do histórico já ingerido: a correção vale para o que for
  sincronizado depois, e o reprocessamento é operação manual existente.
- **Sem taxonomia fechada de motivos**: a tag é texto livre do time; o sistema agrupa e
  normaliza, não valida contra uma lista.

## Impact

- **domain**: nova convenção em `config`; álgebra de intervalos ao lado de `InProgressSpans`
  (precisa servir ao adapter e à aplicação).
- **application**: portas de configuração de plataforma, cálculo da quebra para o dashboard de
  Fluxo, distribuição do painel individual, catálogo de explicações.
- **adapter-out-ado**: reconstrução da linha do tempo de impedimento a partir do mesmo
  histórico de `updates` já buscado por item — **nenhuma chamada HTTP nova**; overlay em
  `WorkItemFlow`; novas chaves de `detail` no evento de work item.
- **adapter-out-persistence**: tabela da convenção + migração (próximo número livre é `V9`;
  `V7` nunca existiu e `V8` já está aplicado, então um `V7` novo quebraria o Flyway).
- **adapter-in-web**: endpoint de admin da convenção, campo novo no payload de Fluxo, e o card
  de fases/drawer na SPA.
- **bootstrap**: default da convenção nas fixtures idempotentes.
- Sem dependência nova, sem fronteira de módulo nova, sem índice ou coluna nova em
  `raw_event` (o `detail` é `jsonb` livre).
