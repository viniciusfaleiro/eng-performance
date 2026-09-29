## 1. UI

- [x] 1.1 `openDrawer` passa a aceitar o nó a consultar, com `state.node` como padrão; as chamadas
      de itens e de pessoas usam esse nó.
- [x] 1.2 Cada card selecionável do painel individual ganha um controle de abertura do detalhamento,
      ao lado do ícone de explicação.
- [x] 1.3 Handler delegado em fase de captura, com `stopPropagation`, para o clique e para
      Enter/Espaço — o card em volta chama `preventDefault()` no próprio `keydown`.
- [x] 1.4 O drawer aberto do painel individual consulta o id da pessoa exibida.

## 2. Verificação

- [x] 2.1 Com Chrome headless: abrir o detalhamento de Throughput, Commits, PRs e % com IA no painel
      de uma pessoa e conferir que a chamada leva `node=p:<pessoa>`.
- [x] 2.2 Conferir que abrir o detalhamento não troca a métrica selecionada do gráfico.
- [x] 2.3 Conferir que os itens listados são os da pessoa, com a navegação num time.
- [x] 2.4 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
