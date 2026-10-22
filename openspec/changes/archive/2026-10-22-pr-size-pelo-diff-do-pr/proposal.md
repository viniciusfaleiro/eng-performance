## Why

O PR Size está inflado a ponto de ser inútil. Caso real: o PR 24145 do repositório
`ASAPay.MerchantManagement` tem **5 arquivos** na aba *Files* do Azure DevOps e aparece na
plataforma com **2309**.

A contagem soma o `changeCounts` de **cada commit** do pull request
(`CommitComments.changedFiles`). Isso infla por dois caminhos independentes:

1. **Commit de merge dentro do PR.** Um "Merge branch 'develop' into feature/x" traz no
   `changeCounts` todos os arquivos vindos da outra branch — que podem ser milhares, e nenhum deles
   é trabalho daquele PR.
2. **O mesmo arquivo em N commits conta N vezes.** Quem mexe cinco vezes no mesmo arquivo soma
   cinco.

A soma por commit nunca foi a pergunta. A pergunta é o tamanho da mudança que o PR propõe, e isso é
o **diff do PR** — o número que a aba *Files* mostra.

A métrica continua sendo de **arquivos**, não de linhas: a API REST de Git do Azure DevOps não expõe
contagem de linhas em lugar nenhum, e já houve uma correção anterior justamente por um rótulo em
linhas sobre um número que não era linha.

## What Changes

- **O PR Size passa a ser o número de arquivos do diff do pull request** — o diff entre a branch de
  destino e a **última iteração** do PR, que é o que a aba *Files* mostra.
- A contagem deixa de depender da varredura commit a commit: commits de merge param de pesar, e um
  arquivo tocado várias vezes conta uma.
- **Sem dado continua sem dado**: iteração ausente ou chamada que falha ⇒ o PR vai **sem** `files`,
  nunca com contagem parcial nem substituto. A falha é por PR, com WARN, e não derruba a sync.
- A busca individual de commit **permanece** onde ela ainda é necessária: a detecção de IA depende
  da mensagem não truncada.
- **BREAKING (leitura)**: a correção vale para o que for **sincronizado depois**. PRs já ingeridos
  mantêm o número inflado até um **reprocessamento** dos eventos (operação manual existente).

## Capabilities

### Modified Capabilities
- `ado-integration`: a contagem de arquivos de um pull request passa a vir do diff da última
  iteração, e não da soma dos commits.
- `flow-dashboard`: o PR Size passa a ser definido como os arquivos do diff do PR.

## Non-goals

- **Não mudar para linhas** — a fonte não tem.
- **Não mexer** em domínio, aplicação ou catálogo: a chave segue `detail.files` e a unidade segue
  "arquivos".
- **Não remover** a busca individual de commit que serve à detecção de IA.
- **Não reprocessar** o histórico automaticamente.

## Impact

- **adapter-out-ado**: leitura nova das iterações e do diff do PR; `PullRequestSync` passa a usá-la;
  o que ficar sem uso em `CommitComments` sai, com seus testes.
- **application**: só texto — o comentário de `pr_size` no catálogo e a explicação da métrica.
- Sem migração, sem chave nova, sem mudança de contrato REST.
