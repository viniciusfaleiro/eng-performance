## 1. O intervalo em progresso como um tipo só

- [x] 1.1 Tipo novo que responde "que intervalos este item ficou em progresso?": parse do formato
      `spans`, recorte a uma janela, teste de sobreposição e soma de horas.
- [x] 1.2 O último intervalo de um item sem conclusão vai até o relógio informado pelo chamador, e
      não até o da ingestão; o flag `in_progress` é o que distingue os dois casos.
- [x] 1.3 A distribuição de horas do painel individual passa a usar esse tipo, deixando de parsear o
      formato por conta própria.
- [x] 1.4 Testes: buraco entre intervalos não conta; item aberto alcança o relógio da leitura; item
      sem histórico não produz intervalo; formato malformado não derruba a leitura.

## 2. Instante ou intervalo, declarado no catálogo

- [x] 2.1 `MetricDefinition` passa a declarar como o evento ocupa o tempo, com instante como padrão
      para não mexer em nenhuma métrica existente.
- [x] 2.2 `inBucket` ramifica nisso — é o único ponto onde pertencimento ao período é decidido, e é o
      que mantém card, gráfico e drill-down concordando.
- [x] 2.3 O motor recebe o relógio da leitura por onde `inBucket` alcança.
- [ ] 2.4 Testes: métrica de instante não muda de comportamento; métrica de intervalo conta por
      sobreposição; a lista do drill-down é exatamente o conjunto contado.

## 3. WIP por intervalo

- [x] 3.1 WIP passa a ser declarado como métrica de intervalo.
- [x] 3.2 O filtro de população do WIP deixa de exigir `in_progress` e passa a exigir apenas que o
      item tenha entrado em estado de trabalho — quem decide o período é a sobreposição.
- [x] 3.3 A busca de eventos do WIP passa a ser do corpus, não de uma janela de datas; método novo no
      port para isso.
- [x] 3.4 Testes: item aberto e intocado há semanas conta hoje; item trabalhado em junho e concluído
      em agosto conta em junho; item que só ficou no backlog não conta; o valor de um mês fechado é o
      mesmo antes e depois da conclusão do item; a semana não é a soma dos dias.

## 4. Fases e eficiência com população de concluídos

- [x] 4.1 `flow_efficiency`, `active_time`, `waiting_time` e `review_time` passam a usar o filtro de
      conclusão.
- [x] 4.2 Testes: item sem histórico aproveitável não entra na razão da eficiência como zero sobre
      um; item inacabado não contribui hora parcial para as fases; as fases passam a descrever a
      mesma população do Cycle Time.

## 5. As explicações passam a descrever a conta

- [x] 5.1 WIP: contagem de itens cujo intervalo em progresso cruza o período, item aberto alcança a
      leitura, inclui item concluído no período, e o aviso de que não é aditiva entre períodos.
- [x] 5.2 Flow Efficiency, Ativo, Espera e Review: só item concluído, e o que fica de fora.
- [x] 5.3 Cycle Time, Lead Time e Flow Lead Time: a duração inteira é atribuída ao período da
      conclusão, mesmo que o trabalho tenha atravessado períodos anteriores.
- [x] 5.4 As demais explicações ganham a frase de onde o item cai no tempo; nenhuma métrica fica sem
      ela.
- [x] 5.5 Teste de guarda: toda métrica do catálogo tem explicação, e a explicação do WIP não promete
      comportamento que a conta não tem.

## 6. Verificação

- [x] 6.1 Com o app local: o WIP deixa de ser zero no protótipo semeado e a lista do drill-down abre
      com os itens contados.
- [x] 6.2 Conferir na tela que Ativo+Espera+Review e Cycle Time descrevem a mesma população.
- [x] 6.3 Ler o mesmo mês fechado duas vezes e confirmar que o WIP não mudou.
- [x] 6.6 Fixture do protótipo: o work item diário passa a ter conclusão e fases, e só a trinca
      semanal das duas últimas semanas fica aberta. Sem conclusão nenhuma, throughput, cycle time,
      lead time, as fases e a eficiência liam zero e o dashboard de Fluxo não era conferível; com
      toda trinca aberta, o WIP acumulava seis meses e passava de uma centena de itens por time.
- [x] 6.4 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
- [ ] 6.5 Bump do último dígito da versão, publicado à parte, conforme a regra do CLAUDE.md.
