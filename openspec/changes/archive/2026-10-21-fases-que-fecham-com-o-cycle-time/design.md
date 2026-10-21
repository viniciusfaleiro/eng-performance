## Context

Quatro números da mesma tela discordam, e cada um por um motivo diferente.

**Fases.** `MetricCatalog.POPULATIONS` usa `completed()` para as três fases, o mesmo nome da
população do Cycle Time — mas não o mesmo efeito. O card lê `cycle_h`, que
`WorkItemFlow.of` só grava quando houve `firstWork`; sem ele a medida é **ausente** e o item sai da
mediana (`MetricsEngine.measure`). Já `fill` grava `active_h = wait_h = review_h = 0` para o mesmo
item, e **zero não é ausência**: ele entra nas fases. O comentário no catálogo afirma que "as fases
descrevem a mesma população do Cycle Time"; não descrevem.

**Valor.** `FlowDashboardService` monta cada fase como a mediana da própria fase. Por item
`ativo + review + espera = ciclo`, mas mediana das partes ≠ mediana do todo — e uma fase que a
maioria pula tem mediana zero por mais tempo que os poucos itens que passaram por ela tenham ficado
lá. A spec promovida carrega as duas versões do fato: "need not sum" num requisito, "always add up"
noutro.

**Drawer.** `ImpedimentBreakdown` soma `wait_h` e `impeded:<motivo>` da população e devolve horas
absolutas. A fase ao lado é mediana por item. Escalas diferentes, nunca batem.

**Motivos.** `ImpedimentTimeline.hoursByReason` recorta e soma cada motivo **independentemente**, de
modo que um trecho com duas tags conta inteiro nas duas; o excedente some no `Math.max(0, …)` do
residual.

**Gargalo.** `dashboardFluxo` usa `phaseSplit` — pesos fixos do protótipo mais hash — e rotula "h em
média". Nunca leu o dado real.

## Goals / Non-Goals

**Goals:**
- As três fases somarem o Cycle Time do card, em qualquer nó, frequência e filtro.
- O drawer da Espera somar a Espera exibida.
- Motivos simultâneos pararem de contar em dobro.
- O resumo de gargalo ler o board.

**Non-Goals:**
- Mudar o valor do card de Cycle Time, o Flow Efficiency, o `StateClassifier`, a convenção de
  impedimento ou a extração do motivo.
- Tocar em painel individual, heatmap, persistência ou ingestão além de `hoursByReason`.

## Decisions

### D1. A população das fases passa a ser "concluído **com ciclo**"

`cycled()` = `completed()` **e** `detail.containsKey("cycle_h")`, aplicada às três fases. É a
correção mínima que faz o nome do comentário virar verdade.

`flow_efficiency` **fica** em `completed()`: item sem ciclo grava `num = den = 0` e não pesa numa
razão, então incluí-lo não distorce — e tirá-lo mudaria uma métrica que ninguém reclamou.

Alternativa descartada: deixar de gravar `active_h`/`wait_h`/`review_h` quando não há ciclo. Mexeria
na ingestão para resolver um problema de leitura, e exigiria reprocessamento para valer no
histórico — enquanto esta correção vale imediatamente.

### D2. Fase = participação média aplicada ao card

```
Σfase     = soma das horas da fase sobre a população
fase_exib = CycleTime_card × Σfase / (Σespera + Σativo + Σreview)
```

A divisão entre as fases é a participação média ponderada por horas; o valor é essa participação
aplicada ao número que o leitor está vendo. As três somam o card por construção.

Três alternativas foram consideradas e descartadas: **mediana por fase** é o bug atual; **soma de
horas** não bate com o card e muda a unidade do que a tela mostra; **média literal por fase** somaria
a *média* do ciclo, que é um número diferente do card — e o card é o que o leitor confere.

Σtotal zero, ou card ausente, ⇒ as três fases são zero. Sempre recomputado sobre a população do nó;
nunca composto a partir dos filhos (regra do motor: nunca média de médias).

### D3. O drawer é escalado para a Espera exibida

As participações por motivo continuam sendo calculadas sobre a população (é onde elas existem), e o
resultado é multiplicado pela Espera exibida. `ImpedimentReasons` ganha `populationHours` (a soma
bruta de hoje) e `scaledTo(waitingShown)`; `FlowDashboardService` aplica a escala com a Espera que
ele mesmo acabou de calcular, de modo que a regra viva num ponto só.

**Proteção para dado antigo:** se os motivos da população excederem `Σespera` — o que o bug de D4
produziu —, eles são reduzidos proporcionalmente para caber, e o residual vai a zero. É preferível a
truncar no residual, que escondia a diferença em vez de absorvê-la.

### D4. Motivos simultâneos repartem o trecho, por trechos elementares

Varrer as fronteiras (dos intervalos impedidos recortados às janelas contadas e dos intervalos de
cada motivo), e em cada trecho elementar dividir a duração pelo número de motivos que o cobrem.
Nenhum motivo cobrindo ⇒ bucket reservado (campo marcado sem tag). A invariante que isso garante é
a que falta hoje: **a soma dos motivos do item = as horas impedidas dele**.

Sem chave nova em `detail`, sem mudança de assinatura, sem chamada HTTP nova. Vale para o que for
sincronizado depois; até o reprocessamento, D3 segura a leitura.

### D5. O gargalo lê `flowPhases()`

E, sem dado real, não nomeia fase. Um resumo que nomeia a fase errada é pior que um resumo vazio:
ele é a primeira frase que o time lê, e estava contradizendo o gráfico logo acima.

## Risks / Trade-offs

- **Os números das fases mudam para todo o histórico** → é o objetivo (hoje estão errados), mas quem
  comparar com um print antigo vai ver diferença; a explicação do "i" tem de dizer a regra nova.
- **Escalar o drawer esconde a magnitude absoluta** → mitigado por manter a soma bruta da população
  como contexto rotulado, e pela lista de itens, que segue mostrando a espera real de cada um.
- **A redução proporcional de dado antigo mascara o bug da ingestão** → mitigado por ela existir só
  até o reprocessamento e estar dita na spec e no texto do "i".
- **A fórmula pode surpreender quem espera "média de horas"** → é o preço de as três somarem o card;
  o texto explica em uma frase, e a alternativa era um resumo que não fecha.
- **Cobertura JaCoCo (70/60 em domain+application)** → a composição das fases e a escala do drawer
  nascem na aplicação e precisam de teste próprio.

## Migration Plan

1. Deploy sem janela: nada de schema. R1–R3 e R5 valem na leitura, inclusive para o histórico.
2. A repartição de motivos simultâneos vale a partir do próximo sync; o histórico só se alinha com
   reprocessamento (operação manual existente), e a proteção de D3 mantém o drawer coerente até lá.
3. Rollback: reverter o código; nada fica gravado de novo.

## Open Questions

- Vale expor, em algum lugar da tela, a soma bruta de horas por fase (não só a da espera)? Hoje o
  produto não mostra horas absolutas de fase em lugar nenhum, e inventá-las aqui seria escopo novo.
