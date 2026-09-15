## 1. Domínio — o período vira um conceito

- [x] 1.1 Criar `Period` em `domain/metrics`: frequência + data de início do balde, construído a
      partir de qualquer data interna (`Frequency.bucketStart`), com `previous()`, `next()` e
      `contains(date)`.
- [x] 1.2 `Period` sabe dizer se está em andamento em relação a uma data de referência — é o que
      decide entre comparação por fatia decorrida e comparação em cheio.
- [x] 1.3 Rejeitar período que ainda não começou, com mensagem que diferencie isso de "sem dados".
- [x] 1.4 Testes de domínio: resolução de data interna para o balde nas três frequências, limites de
      semana ISO e de virada de mês, `previous()` atravessando ano.

## 2. Motor — deixar de assumir o presente

- [x] 2.1 `MetricsEngine`: a comparação por fatia decorrida passa a valer apenas quando o balde
      computado contém o hoje do relógio, não quando é o último da série.
- [x] 2.2 `MetricsService`: os métodos passam a receber o período; o relógio permanece como origem
      do padrão e da noção de "futuro".
- [x] 2.3 Mesma mudança nos serviços de dashboard (DORA, Fluxo, IA), heatmap e individual.
- [x] 2.4 A janela da série passa a terminar no período selecionado.
- [x] 2.5 Teste: card, série e drilldown do mesmo período cobrem exatamente o mesmo intervalo.
- [x] 2.6 Teste: período encerrado compara em cheio; período corrente compara por fatia decorrida.
- [x] 2.7 Teste: período passado sem eventos responde zero com cobertura, não erro.

## 3. Painel individual

- [x] 3.1 Seções ancoradas no balde passam a ancorar no período selecionado.
- [x] 3.2 Janelas longas (calendário de 12 meses, aderência às convenções) passam a **terminar** no
      período selecionado, mantendo a duração.
- [x] 3.3 Teste das duas coisas acima — é a parte mais fácil de quebrar sem perceber.

## 4. Web — contrato

- [x] 4.1 Parâmetro `period` (data ISO) **opcional** nos endpoints de métrica, dashboards, heatmap e
      individual; omitido = período corrente.
- [x] 4.2 Período futuro responde 400 com detalhe legível; período sem dados responde 200.
- [x] 4.3 Documentar em `docs/api/openapi.yaml`, incluindo a regra de normalização (qualquer data
      dentro do balde resolve para o balde).
- [x] 4.4 Teste de API dos três casos: sem parâmetro, período passado, período futuro.

## 5. UI

- [x] 5.1 Controle de período ao lado do seletor de frequência: setas `‹ ›`, rótulo clicável e
      atalho de volta ao corrente.
- [x] 5.2 Rótulo formatado por frequência: `15/07/2026`, `Semana de 13/07`, `Julho/2026`.
- [x] 5.3 Lista de períodos recentes ao clicar no rótulo, com o corrente identificado.
- [x] 5.4 Destaque visual quando o período exibido **não** é o corrente — o risco real é a pessoa
      ler números antigos como atuais.
- [x] 5.5 Propagar o período para todas as chamadas e invalidar os caches de tela por período.
- [x] 5.6 Nó, frequência e período na URL; abrir a URL restaura o estado.
- [x] 5.7 Setas e lista acionáveis por teclado.

## 6. Verificação

- [x] 6.1 Com Chrome headless: escolher um mês passado e conferir que cards, tendência, heatmap e
      painel individual mostram o mesmo período.
- [x] 6.2 Conferir que o drilldown de um card lista itens daquele período.
- [x] 6.3 Copiar a URL com período selecionado, abrir em aba nova e confirmar que restaura.
- [x] 6.4 Conferir que um período anterior à ingestão se lê como "sem dados" (cobertura zero) e não
      como time parado.
- [x] 6.5 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
