## Context

`PullRequestSync.fetch` lê `{org}/{proj}/_apis/git/repositories/{repo}/pullrequests?...` e passa
cada PR para `AdoMapper.pullRequest`, que grava `detail["url"] = webLink(pr)` — isto é,
`pr._links.web.href`. A lista não devolve esse campo, então a URL sai vazia e o drawer renderiza
`<div>` em vez de `<a>`.

O mesmo já aconteceu com work item, e lá a correção foi construir o endereço (change
`link-do-work-item`). O javadoc daquele mapper até registra que o batch GET "carrega nenhum
`_links.web.href` para reusar" — a lição estava escrita ao lado do defeito que sobreviveu.

## Goals / Non-Goals

**Goals:** todo pull request ingerido ter endereço, e as reviews reusarem o mesmo.

**Non-Goals:** cálculo de métrica, reprocessamento automático, links de commit/build/work item.

## Decisions

### D1. Construir sempre, em vez de preferir o campo quando ele vier

`https://dev.azure.com/{org}/{projeto}/_git/{repo}/pullrequest/{id}` é correto por construção e
igual em qualquer ambiente. Preferir `_links.web.href` quando presente deixaria o formato do
endereço dependendo de qual endpoint respondeu — e foi exatamente essa dependência que produziu o
defeito. Um formato só é também um teste só.

### D2. A base web é montada onde a base de API já é

`AdoEventSource` monta `{org}/{proj}/_apis/git/repositories/{repo}`; a base web sai do mesmo lugar,
com o mesmo encode de caminho, e desce para o mapper por `PullRequestSync`. O mapper continua puro:
ele recebe o prefixo, não o monta.

### D3. A fixture passa a ter a forma que a API responde

`pr.json` foi escrita à mão **com** `_links.web`, então o teste passava enquanto a produção falhava.
Entra uma fixture sem `_links`, e o teste do link passa a cobrir o caso real. A antiga continua para
o resto das asserções.

## Risks / Trade-offs

- **Eventos já gravados mantêm a URL vazia** → só o reprocessamento alinha; não há migração que
  conserte sem reler a fonte.
- **Nome de repositório ou projeto com espaço** → precisa do mesmo encode de caminho do resto, senão
  o link passa de ausente a quebrado.
- **O formato do endereço vira responsabilidade nossa** → se o Azure DevOps mudar o padrão de URL,
  quebra; é o preço de não depender de um campo que a lista não manda, e o id na URL é estável há
  muitas versões da API.
