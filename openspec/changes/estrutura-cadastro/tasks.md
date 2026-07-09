## 1. Domínio (module `domain`, sem Spring)

- [x] 1.1 Criar `Vertical`, `Team`, `Person` como raízes com identidade própria e fábricas que validam campos obrigatórios
- [x] 1.2 Criar `TeamMembership { teamId, start, end? }` como entidade filha de `Person`; expor `currentTeam()` (membership sem `end`)
- [x] 1.3 Impor invariantes: hierarquia fixa 3 níveis, no máx. 1 membership aberto por pessoa, gestor = `Person` registrada; lançar exceções de domínio
- [x] 1.4 Implementar `Person.moveToTeam(teamId, effectiveDate)` = fecha membership vigente em (effectiveDate−1d) e abre novo em effectiveDate (as-of-event)
- [x] 1.5 Criar `Repository { key, teamId? }` com regra 1 repo→1 time e `CommitterIdentity { identity, displayName, personId?, commitCount }`
- [x] 1.6 Testes de domínio cobrindo cada invariante e o move-with-history

## 2. Aplicação (module `application`, sem Spring)

- [x] 2.1 Definir a porta de saída `StructureRepositoryPort` (CRUD de verticais/times/pessoas, repos, identidades)
- [x] 2.2 Use-cases de estrutura: criar/editar/remover Vertical, Team, Person; definir gestor de time e de vertical; montar a árvore
- [x] 2.3 Use-case `MovePersonToTeam` (preserva histórico)
- [x] 2.4 Use-cases de identidade: listar descobertas, vincular/desvincular a uma Pessoa; calcular cobertura (atribuídos/total a partir de `commitCount`)
- [x] 2.5 Use-cases de repositório: listar, mapear repo→time (1:1), sinalizar não mapeado
- [x] 2.6 Testes de aplicação com um fake da porta cobrindo os fluxos

## 3. Adapter de saída (module `adapter-out-persistence`)

- [x] 3.1 Implementar `InMemoryStructureRepository` (mapas + cópias defensivas) satisfazendo `StructureRepositoryPort`
- [x] 3.2 Teste do adapter in-memory (round-trip de cada entidade)

## 4. Adapter web (module `adapter-in-web`)

- [x] 4.1 Controllers REST conforme `api/openapi.yaml`: `/structure/tree`, `/admin/verticals`, `/admin/teams`, `/admin/people`, `/admin/people/{id}/team-change`
- [x] 4.2 Controllers de `/admin/ado/committers` (listar + `POST` de vínculo) e `/admin/coverage` (global, sobre fixtures)
- [x] 4.3 DTOs de request/response + tradução de exceções de domínio para 422/409
- [x] 4.4 Telas (Thymeleaf, design system do protótipo) de Admin **Estrutura**, **Identidades** e **Repositórios**, espelhando `prototype/`
- [x] 4.5 Testes de web (MockMvc) dos endpoints e dos códigos de erro

## 5. Composição e fixtures (module `bootstrap`)

- [x] 5.1 Wiring dos use-cases → `InMemoryStructureRepository` (composition root)
- [x] 5.2 Semear fixtures (verticais/times/pessoas/identidades/repos do protótipo, incluindo não atribuídos)
- [x] 5.3 Manter o echo slice como smoke por ora (remoção fica para o S2)

## 6. Fronteiras e fechamento

- [x] 6.1 Garantir ArchUnit verde (domínio sem Spring; adapters não se cruzam)
- [x] 6.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL
