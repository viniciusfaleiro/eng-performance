## Context

`PullRequestSync` (linha ~77) chama `CommitComments.changedFiles(base, prCommits, token)`, que busca
cada commit do PR em `/commits/{id}?changeCount=1` e **soma** os `changeCounts`. O total vai para
`detail.files` em `AdoMapper.pullRequest`.

A mesma busca individual serve à detecção de IA (`anyAi`/`full`), porque a lista de commits trunca a
mensagem em ~400 caracteres e é no corpo que vivem os trailers de IA. Essa parte continua
necessária.

O Azure DevOps expõe o diff do PR em duas chamadas: as **iterações** do PR e as **mudanças** de uma
iteração comparada com a base.

## Goals / Non-Goals

**Goals:** o PR Size ser o número de arquivos do diff do PR, igual à aba *Files*; ausência continuar
sendo ausência; falha num PR não derrubar a sync.

**Non-Goals:** linhas; mexer em domínio, aplicação ou catálogo; remover a busca de commit que serve
à IA; reprocessar histórico.

## Decisions

### D1. A contagem vem da última iteração comparada com a base

```
GET {base}/pullRequests/{id}/iterations            → maior id = última
GET {base}/pullRequests/{id}/iterations/{it}/changes?$compareTo=0&$top=2000
```

`$compareTo=0` é o diff contra a base do PR, que é exatamente o que a aba *Files* mostra. A última
iteração é a versão atual do PR; iterações anteriores são o histórico de pushes, e contá-las
repetiria arquivo pela mesma razão que a soma por commit repetia.

### D2. Paginação pelo `nextSkip` que a própria resposta devolve

Enquanto a resposta trouxer `nextSkip`, repete-se com `$skip=<valor>`. Parar na primeira página
cortaria PRs grandes **em silêncio** — o número viria plausível e baixo, que é o tipo de erro que
ninguém percebe na tela.

### D3. Arquivo é arquivo; pasta não conta

Entradas com `item.isFolder` ou `item.gitObjectType == "tree"` são descartadas. Rename é **uma**
entrada e conta um, mesmo quando o `changeType` vem combinado (`"rename, edit"`).

### D4. Ausência continua ausência, e a falha é por PR

Sem iteração, ou qualquer chamada falhando, o PR vai com `OptionalInt.empty()` — nunca contagem
parcial, nunca substituto. É a mesma regra que a soma por commit já seguia, e a razão é a mesma:
um número parcial é indistinguível de uma medição legítima. O WARN leva o id do PR, para a
investigação começar sabendo qual.

### D5. Uma classe nova, no padrão das irmãs

`PullRequestFiles`, ao lado de `VoteHistory` e `PullRequestWorkTypes`: `final class`, construtor
privado, método `static` que recebe o `AdoRestClient`. Mantém `CommitComments` com uma
responsabilidade só — a mensagem do commit — em vez de duas que já não se encontram.

`commitFiles`/`changedFiles` ficam sem uso e saem, com seus testes: código que ninguém chama é
código que a próxima pessoa acha e acredita.

## Risks / Trade-offs

- **Duas chamadas a mais por PR** (iterações + mudanças, mais páginas em PR grande) → em troca, a
  contagem deixa de depender de buscar cada commit por causa dela. O contador de requisições
  continua observável no resumo da sync.
- **Histórico inflado até reprocessar** → está dito na proposal como BREAKING (leitura); sem
  reprocessamento, períodos antigos seguem com o número velho.
- **`$top=2000` com paginação** → PR gigante custa mais páginas; preferível a truncar.
- **Dependemos do formato de `changeEntries`** → mitigado por descartar pasta explicitamente e
  contar entradas, que é o que a aba *Files* faz.
