## 1. Ingestão

- [ ] 1.1 Acrescentar `System.TeamProject` ao `fields=` do batch GET de work items em `AdoEventSource.fetchWorkItems` — sem chamada nova.
- [ ] 1.2 `AdoMapper.workItem(...)`: montar o endereço pela forma que o Azure DevOps resolve por id (`{org}/_workitems/edit/{id}`), usando o projeto **do item** quando ele vier, e nunca o projeto do laço; aplicar o mesmo encode de caminho que o chamador já usa, para nome de projeto com espaço.
- [ ] 1.3 Gravar o projeto do item no `detail` (`ado_project`), no mesmo padrão de `ado_type`/`ado_parent_type`.

## 2. Testes

- [ ] 2.1 `AdoMapperTest`: item com `System.TeamProject` diferente do projeto passado pelo chamador → a URL e o `ado_project` seguem o item, não o chamador.
- [ ] 2.2 `AdoMapperTest`: item sem `System.TeamProject` no payload → URL na forma por id, sem projeto, e sem o projeto do laço.
- [ ] 2.3 `AdoMapperTest`: projeto com espaço no nome → caminho encodado, link válido.
- [ ] 2.4 `AdoEventSourceTest`: o `fields=` do batch inclui `System.TeamProject` e o número de chamadas não muda.

## 3. Fechamento

- [ ] 3.1 Checagem ponta a ponta: sincronizar e conferir no drill-down do Cycle Time que itens de projetos diferentes abrem com o próprio projeto (ou pela forma por id), e que o `ado_project` gravado bate com o board.
- [ ] 3.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
