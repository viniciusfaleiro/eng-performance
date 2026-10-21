## Why

No drill-down do PR Size — e em qualquer outro que liste pull requests — os itens **não abrem**.
Aparecem na lista, com título e medida, mas não são link.

A URL do PR é lida de `_links.web.href` do objeto do pull request (`AdoMapper.pullRequest`), e os PRs
são buscados pela **lista** (`/pullrequests?searchCriteria.status=completed`), que **não traz esse
campo**. Sem ele a URL fica vazia, e a lista renderiza a linha como texto em vez de link.

É o mesmo defeito de classe que corrigimos no work item: lá o batch GET também não carrega
`_links.web.href`, e a solução foi **construir** o endereço a partir do que já se sabe. Aqui o link
continuou dependendo de um campo opcional que a resposta não manda — e a fixture do teste, feita à
mão **com** `_links.web`, garantia justamente o caminho que a API real não entrega.

O estrago é o de sempre com drill-down: a lista existe para o time conferir o número item a item, e
item que não abre não se confere.

## What Changes

- **O endereço do pull request passa a ser construído** a partir da organização, do projeto, do
  repositório e do id — tudo já conhecido na ingestão —, em vez de lido de um campo que a lista de
  PRs não traz.
- **As reviews acompanham**: elas não têm registro próprio no Azure DevOps e reusam o link do PR.
- **A fixture de teste deixa de garantir o caminho feliz**: passa a existir um pull request sem
  `_links`, que é a forma como a API responde de fato.
- Correção vale para o que for **sincronizado depois**; eventos já gravados mantêm a URL vazia até
  um reprocessamento.

## Capabilities

### Modified Capabilities
- `ado-integration`: o endereço de um pull request (e das reviews que o reusam) passa a ser derivado
  do repositório e do id, não do payload.

## Non-goals

- **Não mexer no cálculo** do PR Size nem de nenhuma outra métrica.
- **Não reprocessar** o histórico automaticamente.
- **Não tocar** em links de commit (que vêm prontos em `remoteUrl`), de build ou de work item.

## Impact

- **adapter-out-ado**: `AdoMapper.pullRequest`/`reviews` recebem a base web do repositório;
  `PullRequestSync`/`AdoEventSource` a constroem ao lado da base de API que já montam.
- Sem migração, sem chave nova em `raw_event.detail`, sem chamada HTTP a mais.
