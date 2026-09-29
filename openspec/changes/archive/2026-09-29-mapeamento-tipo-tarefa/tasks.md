## 1. Mapeamento

- [x] 1.1 `AdoMapper.workType(adoType, parentAdoType)` — package-private, delegando o switch fixo a
      um `baseWorkType(String)` privado; Task resolve pelo tipo do pai, com fallback `docs` quando o
      pai é nulo, vazio ou também Task.
- [x] 1.2 Epic passa a `feature`; "Tech Debt" e "Documentation or Other" entram explicitamente.
- [x] 1.3 `AdoMapper.workItem(...)` recebe `parentAdoType` e o repassa.

## 2. Resolução dos pais

- [x] 2.1 Nova classe `ParentTypeResolver` (final, construtor privado, estática), que calcula os
      pais faltantes, pagina em lotes de 200, faz o GET pedindo só o tipo e mescla no índice.
- [x] 2.2 Degradar em WARN quando a chamada falha, sem interromper a sincronização.
- [x] 2.3 Self-parent tratado como não resolvido.

## 3. Ingestão

- [x] 3.1 `System.Parent` entra no `fields` do GET em lote.
- [x] 3.2 `fetchWorkItems` reestruturado em coleta → resolução → montagem, repassando o tipo do pai.

## 4. Testes

- [x] 4.1 `AdoMapperTest`: matriz do mapeamento, incluindo case-insensitive, Epic→feature, os dois
      tipos customizados, Task com cada tipo de pai, Task sem pai, Task de Task e tipo desconhecido.
- [x] 4.2 Fixture `workitem-task.json` e um teste de fiação por `workItem(...)`.
- [x] 4.3 `ParentTypeResolverTest`: paginação acima de 200, dedup, degradação em exceção.
- [x] 4.4 `AdoEventSourceTest`: pai no mesmo lote (zero chamadas extras), dois Tasks com o mesmo pai
      (uma chamada), pai irresolúvel (fallback, sync segue), pai também Task.

## 5. Verificação

- [x] 5.1 `./gradlew :adapter-out-ado:test :architecture-tests:test`
- [x] 5.2 `./gradlew spotlessApply && ./gradlew build` com BUILD SUCCESSFUL.
