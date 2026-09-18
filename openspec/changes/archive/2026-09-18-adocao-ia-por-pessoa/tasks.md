## 1. Aplicação

- [x] 1.1 `AiDashboardService.children()`: um time passa a ter como filhos as pessoas do time.
- [x] 1.2 `childType()` passa a responder `person` para time; pessoa continua sem filhos.
- [x] 1.3 Testes: time produz ranking das próprias pessoas; pessoa continua sem ranking.

## 2. Web — escopo

- [x] 2.1 O filtro do ranking passa a usar `canViewIndividual` para pessoa e `canView` para nó de
      estrutura, como o heatmap já faz.
- [x] 2.2 Teste de API: quem vê o time mas não as pessoas individualmente não recebe as pessoas.

## 3. UI

- [x] 3.1 `snap()` marca o retorno quando nada real foi encontrado — distinguindo **carregando** de
      **ausente**, senão o card pisca "sem dados" antes da resposta chegar.
- [x] 3.2 Card de métrica, ranking, faixa de estatísticas e donut passam a exibir "sem dados"
      quando a marca está presente.
- [x] 3.3 O rótulo do painel acompanha o tipo de filho (`por vertical` / `por time` / `por pessoa`).

## 4. Verificação

- [x] 4.1 Com Chrome headless: no nível de time o ranking lista pessoas com valores reais; num nó
      sem dados o painel diz "sem dados" em vez de zeros.
- [x] 4.2 Conferir que um zero medido continua aparecendo como zero.
- [x] 4.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
