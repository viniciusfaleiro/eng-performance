## Why

O card **PR Size (médio)** mostra "linhas" e não mede linhas. Medido com o payload de commit gravado
do org real: três commits num PR produzem `lines = 3`. O número exibido é a **contagem de commits**
do PR.

A causa é uma linha do mapper (`AdoMapper.java:95`):

```java
detail.put("lines", Long.toString(hasCounts ? lines : count));
```

`hasCounts` só é verdadeiro se algum commit traz um objeto `changeCounts`. Nenhum traz: o endpoint de
commits do PR devolve referências magras, e o único payload de commit gravado do org real tem quatro
campos (`commitId`, `comment`, `author`, `remoteUrl`). O fallback entra sempre.

Três coisas conspiraram para isso durar:

- **O teste prova o caminho que não acontece.** `pullRequestMapsCycleFirstPassAndLink` monta o JSON à
  mão com `changeCounts` injetado e afirma `lines = 20`. Verde para sempre, sobre uma forma de
  resposta que a API não devolve.
- **O fallback é silencioso e plausível.** Escreve na mesma chave, com a mesma unidade, um número da
  ordem de grandeza de um PR pequeno. A cobertura fica 100% porque a chave *está* presente, então
  nada na tela se declara sem dado — ao contrário da disciplina do resto do arquivo, onde um PR sem
  commits fica explicitamente sem a chave.
- **O protótipo mascara.** O fixture grava números plausíveis direto, então o card mostra ~253
  localmente e parece saudável.

E a documentação oficial da API 7.1 fecha a questão mais de fundo: **linha alterada não existe no
Azure DevOps Git REST**. `changeCounts` é definido como *"Counts of the types of changes (edits,
deletes, etc.) included with the commit"* — contagem de **itens**, não de linhas. Nem `GitCommit`,
nem `GitCommitRef`, nem `GitChange` têm campo de adições/remoções de linha. Ou seja: mesmo no caminho
"feliz" o código mediria arquivos com o rótulo de linhas.

## What Changes

- **PR Size passa a medir arquivos alterados**, que é o maior tamanho honesto que a fonte oferece. O
  card, a unidade e a explicação passam a dizer "arquivos".
- A ingestão passa a **buscar a contagem por commit** com `?changeCount=1`, que é o que faz o
  `changeCounts` aparecer (sem o parâmetro ele não vem — os dois exemplos da doc oficial diferem
  exatamente nisso). Com `changeCount=1` o array `changes` vem truncado em um item mas
  `changeCounts` traz o **total verdadeiro**, então o payload fica mínimo.
- A busca por commit que já existe para recuperar comentário truncado passa a **servir aos dois
  propósitos**, memoizada por commit: uma chamada por commit, não duas.
- **O fallback silencioso morre.** Sem contagem, o PR fica sem a chave, cai da mediana e a cobertura
  do card desce — "sem dado" volta a ser sem dado.
- A chave de detalhe deixa de se chamar `lines` e passa a se chamar `files`, para o dado não
  continuar se apresentando como aquilo que nunca foi.

**BREAKING (números, não API):** o PR Size exibido muda de "mediana de commits por PR" para "mediana
de arquivos por PR" — vai subir. Até o próximo sync, os PRs já ingeridos não têm a contagem e o card
reporta cobertura baixa, o que é a leitura correta: não temos o dado ainda.

## Capabilities

Sem capacidade nova. Modificadas: `ado-integration` (a ingestão passa a registrar arquivos
alterados) e `flow-dashboard` (PR Size é arquivos).

## Impact

- **Custo de sync: uma chamada HTTP a mais por commit de PR.** Com 200 PRs de ~5 commits, ~1.000
  chamadas por repositório por sync. A memoização por commit evita a segunda chamada quando o mesmo
  commit também precisa do comentário completo, e o contador de recargas que já existe passa a medir
  o total — o custo fica medido, não estimado.
- Nada de reprocessamento obrigatório: o card se declara sem dado até o próximo sync preencher.
- **Fora de escopo:** linhas de verdade, que exigiriam baixar os blobs de cada arquivo e fazer o diff
  do nosso lado — N chamadas por commit, mais binário, arquivo grande e renomeação. Decidido com o
  usuário em 07/10/2026, junto da escolha por arquivos.
- Ressalva registrada na explicação do card: uma **pasta criada** aparece como item alterado, então um
  commit que cria diretórios conta um pouco mais que os arquivos que ele toca.
