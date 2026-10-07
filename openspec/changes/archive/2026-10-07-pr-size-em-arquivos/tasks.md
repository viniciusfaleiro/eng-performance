## 1. A contagem por commit na ingestão

- [x] 1.1 A busca por commit passa a levar o parâmetro que faz as contagens aparecerem, e a ser
      memoizada por id — um commit que precisa do comentário completo e da contagem é buscado uma vez.
- [x] 1.2 Método novo que devolve a contagem de itens alterados de um commit, ou nada quando não foi
      possível obter.
- [x] 1.3 O total do PR é a soma dos commits; qualquer commit sem contagem deixa o PR **sem** total.
- [x] 1.4 Falha na busca não derruba o sync: o PR vai sem contagem e o aviso fica no log.
- [x] 1.5 Testes: contagem somada entre commits; um commit sem contagem anula o total do PR; o
      commit é buscado uma vez quando serve aos dois propósitos.

## 2. O evento deixa de mentir o nome

- [x] 2.1 `AdoMapper.pullRequest` passa a receber a contagem resolvida e a gravá-la como `files`,
      apenas quando existe.
- [x] 2.2 O fallback para contagem de commits sai, e a chave `lines` deixa de ser gravada.
- [x] 2.3 Testes: PR com contagem grava `files`; PR sem contagem não grava chave nenhuma; nenhum
      caminho produz `lines`.

## 3. A métrica e o texto

- [x] 3.1 `pr_size` passa a ler `files`, com unidade "arquivos".
- [x] 3.2 A explicação passa a descrever arquivos alterados, dizer que linha alterada não existe na
      fonte, e registrar a ressalva da pasta criada.
- [x] 3.3 A SPA passa a rotular o card em arquivos, inclusive no formato do número.
- [x] 3.4 Teste de guarda: a explicação do PR Size não promete linhas.

## 4. Fixture

- [x] 4.1 O fixture do protótipo grava `files` com contagens de arquivo plausíveis, e deixa de gravar
      `lines`.

## 5. Verificação

- [x] 5.1 Com o app local: o card mostra arquivos e o número bate com o fixture.
- [x] 5.2 Conferir o que um PR sem contagem faz. **Resultado:** cai da mediana e aparece no
      drill-down como não contado, mas a cobertura **não** desce — ela mede atribuição, não presença
      de medida. A spec foi corrigida para dizer isso, e o gap ficou registrado como questão aberta.
- [x] 5.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
- [x] 5.4 Bump do último dígito da versão, publicado à parte, conforme a regra do CLAUDE.md.
