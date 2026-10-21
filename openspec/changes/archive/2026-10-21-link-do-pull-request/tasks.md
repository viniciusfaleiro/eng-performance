## 1. O endereço construído

- [x] 1.1 `AdoEventSource`: montar a base web do repositório (`{org}/{projeto}/_git/{repo}`, com o mesmo encode da base de API) e passar para `PullRequestSync.fetch`.
- [x] 1.2 `PullRequestSync`: repassar a base web para `AdoMapper.pullRequest` e `AdoMapper.reviews`.
- [x] 1.3 `AdoMapper`: o PR e suas reviews gravam `url` como `<base web>/pullrequest/<id>`, sem depender de `_links`.

## 2. Testes

- [x] 2.1 Fixture nova de pull request **sem** `_links`, que é a forma da resposta da lista.
- [x] 2.2 `AdoMapperTest`: o PR ingerido dessa fixture tem link; as reviews dele têm o mesmo link.
- [x] 2.3 Nome de repositório com espaço → caminho encodado.

## 3. Fechamento

- [x] 3.1 Conferir no app: o drawer do PR Size lista itens clicáveis, e o de reviews também.
- [x] 3.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
