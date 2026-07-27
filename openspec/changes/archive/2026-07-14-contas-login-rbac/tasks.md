## 1. Domínio (module `domain`, sem Spring)

- [x] 1.1 `AccessScope` (value object): `canView(nodeId)`, `canViewIndividual(personId)`, flags admin/orgWide
- [x] 1.2 `AccessPolicy.scopeOf(account, structure)` — admin→tudo+config; exec→todas verticais (leitura); senão deriva da Pessoa (gestor de vertical/time, membro)
- [x] 1.3 Testes cobrindo cada persona: admin, exec, gestor de time (coaching), gestor de vertical, membro, e negação fora do escopo

## 2. Aplicação (module `application`, sem Spring)

- [x] 2.1 Estender `PasswordHasher` com `boolean matches(raw, hash)`
- [x] 2.2 Porta `TokenService` (emitir/validar token → claims accountId/role/personId)
- [x] 2.3 Use-case `Login` (verifica senha + status ativo → token), `CurrentUser` (identidade + escopo via `AccessPolicy`), `ChangeOwnPassword` (verifica senha atual)
- [x] 2.4 Serviço de autorização: resolve `AccessScope` de uma conta a partir do `StructureRepositoryPort`
- [x] 2.5 Testes de aplicação (fakes): login ok/credenciais inválidas/conta desativada; troca de senha; resolução de escopo

## 3. Adapters de saída (module `adapter-out-persistence`)

- [x] 3.1 `matches` no `BCryptPasswordHasher`
- [x] 3.2 `JwtTokenService` (assina/valida com segredo de config) + teste unitário

## 4. Adapter web (module `adapter-in-web`)

- [x] 4.1 Controllers `/auth/login`, `/auth/logout`, `/auth/me` (identidade + escopo), `/auth/password`
- [x] 4.2 Filtro que valida o token (`Authorization: Bearer`) e popula o principal; 401 se ausente/inválido em rota protegida
- [x] 4.3 Interceptor de escopo: 403 quando a request acessa um `nodeId`/`personId` fora do `AccessScope`; endpoints de config exigem `admin`
- [x] 4.4 Filtrar `/structure/tree` pelo escopo; DTOs de auth
- [x] 4.5 Testes de web (MockMvc): login/401/403; admin alcança config; membro barrado em time alheio; coaching-only (par não vê indivíduo)

## 5. Composição (module `bootstrap`)

- [x] 5.1 Wiring dos use-cases de auth + `JwtTokenService`; segredo via env `JWT_SECRET` (default de dev)
- [x] 5.2 Remover o echo slice (assumido pelo cadastro/auth como fatia de referência)

## 6. Frontend (protótipo servido)

- [x] 6.1 Tela de login: `POST /auth/login`, guardar o token, enviar `Authorization` nos fetches; em 401 mostrar erro
- [x] 6.2 Navegação/telas respeitam a árvore já filtrada pelo escopo do backend
- [x] 6.3 Parity-test (logado como admin) app vs protótipo = 0px

## 7. Fronteiras e fechamento

- [x] 7.1 ArchUnit verde (domínio sem Spring; `AccessPolicy` pura)
- [x] 7.2 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde
