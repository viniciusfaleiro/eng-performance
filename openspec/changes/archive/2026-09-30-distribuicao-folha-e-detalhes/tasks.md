## 1. Ingestão do pai

- [x] 1.1 `AdoMapper.workItem` passa a gravar o id do pai (`System.Parent`) no detail, ao lado do
      tipo do pai que já grava.
- [x] 1.2 Testes: item com pai grava o id; item sem pai segue válido; pai inacessível não impede a
      ingestão do filho.

## 2. Índice de filhas

- [x] 2.1 Método novo no `EventStorePort`: o conjunto dos ids que aparecem como pai de algum work
      item — ids, não eventos.
- [x] 2.2 Implementação no adapter de persistência como `DISTINCT` sobre a chave do detail.
- [x] 2.3 Testes: conjunto vem do corpus inteiro e não da janela; ausência de pais devolve conjunto
      vazio em vez de falhar.

## 3. Regra de folha na distribuição

- [x] 3.1 A distribuição passa a descartar item cujo id está no conjunto de pais.
- [x] 3.2 Epic nunca contribui, decidido pelo tipo cru.
- [x] 3.3 Sem id de pai em nenhum evento, a distribuição se comporta como hoje — a degradação é o
      comportamento atual, não um número novo.
- [x] 3.4 Pessoa cujos itens são todos contêineres responde "sem trabalho folha no período", não uma
      distribuição de zeros.
- [x] 3.5 Testes: contêiner não disputa hora com as filhas; User Story e Feature folha contam; Bug
      com Tasks filhas não conta; Epic folha não conta; filhas fora do período não mudam a
      classificação.

## 4. Convenção de item de minutos

- [x] 4.1 Novo `ConventionFlag` para item com tempo total em andamento abaixo do limite, nomeando os
      itens.
- [x] 4.2 O item continua na distribuição — o alerta explica a fatia perto de zero, não a esconde.
- [x] 4.3 Testes: item de minutos sinalizado e contado; item de duração plausível não sinalizado.

## 5. Listas auditáveis no painel

- [x] 5.1 A distribuição passa a carregar, por tipo, os itens contabilizados com **as duas horas**
      (corrida no período e contabilizada após o prorrateio), link e título — do mesmo sweep line que
      produz o total, para a coluna contabilizada fechar com a fatia.
- [x] 5.2 As reviews passam a carregar a lista por direção, com decisão e link da PR; recebidas
      listam PRs, nunca agregam por revisor.
- [x] 5.3 Teto por lista com a contagem total ao lado.
- [x] 5.4 Documentar em `docs/api/openapi.yaml`.
- [x] 5.6 Fixture do protótipo: work item ganha título, deep-link e janela em andamento, e uma vez
      por semana uma User Story com duas Tasks filhas — sem contêiner nem link nos dados semeados, o
      drill-down abre vazio e a regra de folha não é demonstrável antes do reprocessamento.
- [x] 5.5 Testes de API: listas presentes e recortadas pelo escopo individual; teto respeitado com o
      total informado; a soma das horas contabilizadas da lista fecha com a hora do tipo.

## 6. UI

- [x] 6.1 A linha de um tipo de trabalho abre o drawer com os itens contabilizados, as duas colunas
      de hora e a indicação de qual delas soma no total.
- [x] 6.2 As linhas de reviews dadas e recebidas abrem suas listas.
- [x] 6.3 Os cards de entrega ganham a seta que torna descobrível o detalhamento que já existe.
- [x] 6.4 Legenda: o total é de horas corridas em andamento, e a hora de cada item é dividida entre os
      itens simultâneos.
- [x] 6.5 O alerta de convenção aparece junto dos demais, com texto de pergunta e não de acusação.

## 7. Verificação

- [x] 7.1 Com Chrome headless: abrir as três listas e conferir que os links abrem o registro certo.
- [x] 7.2 Conferir que a distribuição de uma pessoa com contêineres perde as linhas de contêiner e
      mantém as fatias percentuais na mesma ordem de grandeza.
- [x] 7.3 Conferir que, sem id de pai nos dados, a tela é idêntica à de hoje.
- [x] 7.4 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
