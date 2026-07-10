## Why

A plataforma precisa de um cadastro próprio da estrutura organizacional
(Vertical → Time → Pessoa) antes de qualquer métrica: é ele que define **para
quem** cada evento do Azure DevOps será atribuído. Sem esse cadastro — pessoas e
suas identidades de commit, repositórios por time, gestores — não há como compor
DORA/Fluxo/IA por estrutura. Este é o slice de fundação (S1 do roadmap) sobre o
qual todos os demais dependem.

## What Changes

- Introduz o **domínio de cadastro**: `Vertical`, `Team`, `Person`,
  `TeamMembership` (vínculo com vigência / as-of-event), `Repository`,
  `CommitterIdentity` — substituindo o echo slice como a primeira fatia real.
- Impõe as invariantes do PRD: **hierarquia fixa em 3 níveis**, **1 repositório →
  1 time**, e **gestor de time e de vertical definidos manualmente** pelo Admin.
- Adiciona use-cases de CRUD e a porta de saída `StructureRepositoryPort`, com um
  **adapter in-memory** (fixtures) no padrão do echo slice.
- Expõe os endpoints de estrutura/admin conforme `api/openapi.yaml`:
  `/structure/tree`, `/admin/verticals`, `/admin/teams`, `/admin/people`,
  `/admin/people/{id}/team-change`, `/admin/ado/committers`, `/admin/coverage`.
- Server-renderiza as telas de **Admin: Estrutura, Identidades e
  Repositórios**, espelhando `prototype/`.
- Move a pessoa entre times **preservando o histórico** (encerra o membership
  vigente e abre outro), e mantém identidades/repos não mapeados num balde
  **"Não atribuído"** com indicador de cobertura.

## Non-goals

- **Login e enforcement de RBAC** (S2) — aqui há apenas o **cadastro/CRUD de contas**
  pelo Admin (email/senha/perfil), sem fluxo de autenticação nem checagem de escopo.
- **Sync real do Azure DevOps** (S9) — a config de conexão é persistida; "Testar
  conexão" apenas marca `connected` (não chama o ADO). `committers`/`coverage`
  operam sobre fixtures.
- Eventos crus, ingestão e qualquer métrica/agregação (S3+).
- Métricas/telas de dashboards, comparativo, individual.

## Capabilities

### New Capabilities
- `org-structure`: cadastro e navegação da hierarquia Vertical → Time → Pessoa,
  com membership por vigência (as-of-event), gestores manuais de time e vertical,
  e a árvore de estrutura.
- `committer-identity`: descoberta de identidades de committer e seu vínculo com
  uma Pessoa, com balde "Não atribuído" e indicador de cobertura (sobre fixtures).
- `repository-mapping`: vínculo de repositórios/projetos do ADO a times, com a
  regra 1 repositório → 1 time.
- `user-accounts`: cadastro de contas de login (nome, email, senha hasheada,
  perfil, status), vínculo opcional a uma Pessoa; gerenciado pelo Admin (sem
  login/RBAC, que é S2).
- `platform-config`: configuração singleton da plataforma — conexão com o Azure
  DevOps (persistida, PAT redigido; "testar" marca conectado) e convenção de
  detecção de IA (trailer/tag/regex).

### Modified Capabilities
<!-- Nenhuma: não há specs existentes; o echo slice não é uma capability versionada. -->

## Impact

- **Módulos:** `domain` (novas entidades/invariantes), `application` (use-cases +
  `StructureRepositoryPort`), `adapter-out-persistence` (adapter in-memory),
  `adapter-in-web` (controllers + templates Thymeleaf), `architecture-tests` (mantém as
  fronteiras).
- **Contratos:** implementa o subconjunto de estrutura/admin do
  `api/openapi.yaml`.
- **Substitui** o echo slice como fatia de referência (o echo pode ser removido ou
  mantido como smoke até o S2).
- **Sem** dependências externas novas; tudo continua fechando em `./gradlew build`.
