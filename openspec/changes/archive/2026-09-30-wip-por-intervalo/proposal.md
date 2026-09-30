## Why

O WIP na tela não responde a pergunta que o nome dele faz. Hoje ele conta work items **ainda
abertos cuja última alteração caiu no período** — porque a métrica é declarada como contagem de
eventos (`SUM`, que no motor é `ms.size()`) e os eventos são buscados e agrupados pela **data do
evento**, que para um work item é a conclusão ou a última mudança de estado.

Três consequências, todas verificadas no código:

- Uma tarefa em progresso desde 10/08, aberta e sem ninguém mexer, **não aparece no WIP de hoje**:
  a janela de busca da leitura diária são os 12 dias anteriores, e a data do evento dela é 10/08.
  Quanto mais parado o trabalho, mais invisível na métrica que existe para expor trabalho parado.
- Uma tarefa trabalhada em junho que concluiu em agosto **não aparece no WIP de junho**: a data do
  evento virou 20/08, fora da janela lida.
- O WIP de um mês fechado **muda retroativamente**, porque cada work item é um único evento
  sobrescrito a cada sincronização: ao concluir, o item perde o `in_progress` e a data dele pula
  para a conclusão.

E o texto que a tela mostra no "i" já promete o comportamento correto — *"Itens que estiveram em
andamento em algum momento do período"* — que não é o que a conta faz.

Auditando o catálogo pela mesma classe de defeito ("coisa em andamento medida como se estivesse
pronta"), apareceram mais dois, e um é pior:

- **Flow Efficiency conta item sem dado como 0%.** Um work item sem histórico de estado aproveitável
  não recebe `num`/`den` na ingestão, e o motor cai nos defaults `num=0`, `den=1`. Como a métrica é
  `soma(num) ÷ soma(den)`, cada item sem dado **empurra a eficiência para baixo**. A spec de
  `flow-dashboard` já proíbe isso literalmente — *"Work items with no usable state history SHALL be
  excluded ... never counted as zero"* — então não é mudança de regra, é o código divergindo da
  regra que já existe.
- **Ativo, Espera e Review incluem item inacabado com medida parcial**, porque o filtro de população
  só existe para throughput, cycle time, flow lead time e WIP. Efeito visível: as três fases não
  fecham com o Cycle Time ao lado, por não serem a mesma população.

## What Changes

- **WIP passa a contar item cujo intervalo em progresso cruza o período selecionado** — a definição
  que o texto da tela já promete. Um item em progresso hoje conta no dia; em progresso em algum
  momento do mês, conta no mês.
- O intervalo de um item **ainda aberto passa a ser esticado até o relógio da leitura**, e não o da
  ingestão. Sem isso a leitura diária depende de quando o sincronizador rodou.
- Esse esticamento vale **também para a distribuição de horas do painel individual**, que hoje para
  no relógio da ingestão. Duas noções de "em progresso até quando" na mesma tela não se sustentam.
- A pergunta "que intervalos este item ficou em progresso?" passa a ter **uma implementação só**,
  hoje duplicada entre o motor (que não a tem) e a distribuição (que a parseia inline).
- **Flow Efficiency, Ativo, Espera e Review passam a contar só item concluído** — o que corrige o
  item-sem-dado-como-zero e faz as fases voltarem a fechar com o Cycle Time.
- **Toda explicação de métrica passa a descrever a conta com precisão**: o que entra, o que fica de
  fora, e — o que falta hoje em todas — **a que data ou intervalo o item é atribuído**. É o ponto
  em que o texto e o código divergiram, e é o que torna a divergência detectável na próxima vez.

**BREAKING (números, não API):** Flow Efficiency sobe em qualquer time com itens sem histórico de
estado, possivelmente bastante. WIP muda em toda leitura. As três fases mudam. Nenhum contrato de
API muda de forma.

## Capabilities

Sem capacidade nova. Modificadas: `metrics-engine` (como um evento ocupa o tempo), `flow-dashboard`
(WIP, eficiência e fases), `metric-explanation` (precisão obrigatória), `individual-dashboard`
(fim do intervalo aberto), `metric-drilldown` (a lista do WIP segue a nova regra).

## Impact

- Não depende de reprocessamento: `spans` já é gravado em todo work item desde que o adapter do ADO
  existe. A correção vale para o histórico já ingerido, no deploy.
- O WIP passa a ler todos os work items em vez de uma janela de datas — custo que cresce com o
  corpus, não com o período. Guardar os limites do intervalo em campos indexados é otimização
  registrada no design, com gatilho explícito, e não muda nenhum número.
- Fica **fora de escopo**, para change própria: o filtro de conclusão em `pr_review_time`,
  `code_cycle_time` e `code_throughput`. Hoje estão corretas por a ingestão pedir ao ADO apenas
  `status=completed`; é seguro contra um cenário futuro, não correção de número de hoje.
