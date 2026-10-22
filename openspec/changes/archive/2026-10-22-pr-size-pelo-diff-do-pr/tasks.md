## 1. A contagem pelo diff do PR

- [x] 1.1 Criar `PullRequestFiles` no `adapter-out-ado`, no padrão de `VoteHistory`/`PullRequestWorkTypes`: lê as iterações, escolhe a de maior id, lê as mudanças com `$compareTo=0` e pagina enquanto houver `nextSkip`.
- [x] 1.2 Contar só arquivos: descartar entrada de pasta (`item.isFolder` ou `item.gitObjectType == "tree"`); rename conta um.
- [x] 1.3 Sem iteração ou chamada com falha ⇒ `OptionalInt.empty()`, WARN com o id do PR, e a sync segue.
- [x] 1.4 `PullRequestSync` passa a usar a leitura nova no lugar de `comments.changedFiles(...)`.
- [x] 1.5 Manter a contagem de requisições observável no resumo da sync.

## 2. Limpeza

- [x] 2.1 Remover `CommitComments.changedFiles`/`commitFiles` e os testes deles, já sem uso; manter `full`/`anyAi`, que a detecção de IA usa.
- [x] 2.2 Ajustar o javadoc de `CommitComments`, que hoje descreve as duas responsabilidades.

## 3. Testes (fixtures JSON em `src/test/resources/ado`)

- [x] 3.1 Última iteração com 5 entradas de arquivo → `files = 5`, mesmo com um commit de merge no PR somando milhares.
- [x] 3.2 Arquivo alterado em vários commits → conta 1.
- [x] 3.3 Paginação: duas páginas via `nextSkip` → soma as duas.
- [x] 3.4 Entradas de pasta ignoradas.
- [x] 3.5 Várias iterações → usa a de maior id.
- [x] 3.6 Falha nas iterações ou nas mudanças → PR sem `files`, sync continua.

## 4. Textos

- [x] 4.1 Comentário de `pr_size` em `MetricCatalog`: o número é o dos arquivos do diff do PR (aba *Files*), sem somar commits.
- [x] 4.2 Explicação da métrica (`MetricExplanations`, chave `pr_size`): mesma regra, mais a nota de que commits de merge não inflam e que períodos já sincronizados só mudam após reprocessar.

## 5. Fechamento

- [x] 5.1 Rodar `./gradlew spotlessApply && ./gradlew build` → **BUILD SUCCESSFUL**. Se o Docker Hub recusar a imagem do Testcontainers neste host, rodar `./gradlew build -x :adapter-out-persistence:test` e **declarar** — esta change não toca a persistência.
- [x] 5.2 Registrar que o aceite final (PR 24145 do `ASAPay.MerchantManagement` com 5 arquivos) exige **reprocessar** os eventos, e que isso é operação do usuário.
