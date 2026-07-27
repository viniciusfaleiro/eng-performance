## Why

O S1 cadastra contas (email/senha BCrypt) e a estrutura (Pessoa ↔ Time ↔
Vertical, com gestores), mas **qualquer um acessa qualquer coisa** — não há login
nem checagem de permissão. O S2 fecha isso: autenticação própria na plataforma e
autorização **derivada da estrutura**, para que cada pessoa veja apenas o que lhe
cabe (regra central do PRD: medir para melhorar, sem vigiar).

## What Changes

- Introduz **login próprio** (email/senha) sobre as contas do S1: emissão de
  **sessão** na autenticação, `logout`, `GET /auth/me` (identidade + escopo) e
  troca da **própria senha**. A tela de login do protótipo passa a autenticar de
  verdade.
- Introduz **autorização derivada da estrutura**: o escopo de uma conta vem da
  posição da **Pessoa vinculada**:
  - **gestor de um Time** → vê o time **+ os indivíduos** dele (coaching);
  - **gestor de uma Vertical** → vê a vertical com **times agregados**;
  - **membro** → vê os **próprios** dados + o **agregado do seu time**.
- Dois acessos **explícitos e autoritativos** na conta: **`admin`** → área de
  configuração (contas, integração ADO, convenção de IA) + acesso total; **`exec`**
  → **leitura org-wide** (todas as verticais), sem config. `manager`/`contributor`
  viram rótulo — o escopo real é derivado da estrutura.
- **Enforcement**: endpoints respondem **403** quando a conta acessa um nó fora do
  seu escopo. Visão/comparação de indivíduos é **coaching-only** (só o gestor
  daquele time; nunca leaderboard nem cross-time).

## Non-goals

- Métricas/dashboards e o motor de agregação (S3+); aqui o enforcement é aplicado
  sobre os endpoints de estrutura/admin existentes e sobre a resolução de escopo.
- **SSO / Entra ID / OAuth externo** e recuperação de senha por email — futuro.
- Múltiplas organizações ADO.
- Gestão de papéis granular além de `admin`/`exec` + derivação estrutural.

## Capabilities

### New Capabilities
- `authentication`: login próprio por email/senha sobre as contas do S1, com
  sessão, logout, identidade do usuário atual (`/auth/me`) e troca da própria
  senha; senha sempre verificada contra o hash BCrypt, nunca trafega em claro.
- `authorization`: resolução do **escopo de acesso** de uma conta a partir da
  Pessoa vinculada e da estrutura (gestor de time/vertical, membro) mais os grants
  explícitos `admin`/`exec`; enforcement de 403 fora do escopo; comparação de
  pessoas coaching-only.

### Modified Capabilities
<!-- Nenhuma alteração de requisito nas specs do S1; authorization consome
     user-accounts e org-structure sem mudar seu comportamento. -->

## Impact

- **Módulos:** `domain` (política de escopo pura: dado conta+estrutura → nós
  visíveis), `application` (use-cases de auth + serviço de autorização + porta de
  sessão/token), `adapter-in-web` (endpoints `/auth/*`, filtro/guarda de
  autorização, 403), `adapter-out-persistence` (verificação de senha via o
  `PasswordHasher` já existente; sessão conforme a decisão de design), `bootstrap`
  (wiring).
- **Contratos:** implementa `/auth/login|logout|me|password` do `api/openapi.yaml`
  e adiciona o escopo em `/auth/me`.
- **Frontend:** a tela de login (protótipo) passa a chamar `/auth/login`; a
  navegação/telas respeitam o escopo retornado. Paridade visual mantida.
- **Sem** novas dependências externas de identidade; tudo continua em PostgreSQL e
  fechando em `./gradlew build`.
