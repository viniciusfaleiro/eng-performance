## 1. Domínio (module `domain`, sem Spring)

- [ ] 1.1 `AccessScope` (value object): `canView(nodeId)`, `canViewIndividual(personId)`, flags admin/orgWide
- [ ] 1.2 `AccessPolicy.scopeOf(account, structure)` — admin→tudo+config; exec→todas verticais (leitura); senão deriva da Pessoa (gestor de vertical/time, membro)
- [ ] 1.3 Testes cobrindo cada persona: admin, exec, gestor de time (coaching), gestor de vertical, membro, e negação fora do escopo

## 2. Aplicação (module `application`, sem Spring)

- [ ] 2.1 Estender `PasswordHasher` com `boolean matches(raw, hash)`
- [ ] 2.2 Porta `TokenService` (emitir/validar token → claims accountId/role/personId)
- [ ] 2.3 Use-case `Login` (verifica senha + status ativo → token), `CurrentUser` (identidade + escopo via `AccessPolicy`), `ChangeOwnPassword` (verifica senha atual)
- [ ] 2.4 Serviço de autorização: resolve `AccessScope` de uma conta a partir do `StructureRepositoryPort`
- [ ] 2.5 Testes de aplicação (fakes): login ok/credenciais inválidas/conta desativada; troca de senha; resolução de escopo

## 3. Adapters de saída (module `adapter-out-persistence`)

- [ ] 3.1 `matches` no `BCryptPasswordHasher`
- [ ] 3.2 `JwtTokenService` (assina/valida com segredo de config) + teste unitário

## 4. Adapter web (module `adapter-in-web`)

- [ ] 4.1 Controllers `/auth/login`, `/auth/logout`, `/auth/me` (identidade + escopo), `/auth/password`
- [ ] 4.2 Filtro que valida o token (`Authorization: Bearer`) e popula o principal; 401 se ausente/inválido em rota protegida
- [ ] 4.3 Interceptor de escopo: 403 quando a request acessa um `nodeId`/`personId` fora do `AccessScope`; endpoints de config exigem `admin`
- [ ] 4.4 Filtrar `/structure/tree` pelo escopo; DTOs de auth
- [ ] 4.5 Testes de web (MockMvc): login/401/403; admin alcança config; membro barrado em time alheio; coaching-only (par não vê indivíduo)

## 5. Composição (module `bootstrap`)

- [ ] 5.1 Wiring dos use-cases de auth + `JwtTokenService`; segredo via env `JWT_SECRET` (default de dev)
- [ ] 5.2 Remover o echo slice (assumido pelo cadastro/auth como fatia de referência)

## 6. Frontend (protótipo servido)

- [ ] 6.1 Tela de login: `POST /auth/login`, guardar o token, enviar `Authorization` nos fetches; em 401 mostrar erro
- [ ] 6.2 Navegação/telas respeitam a árvore já filtrada pelo escopo do backend
- [ ] 6.3 Parity-test (logado como admin) app vs protótipo = 0px

## 7. Fronteiras e fechamento

- [ ] 7.1 ArchUnit verde (domínio sem Spring; `AccessPolicy` pura)
- [ ] 7.2 `docker compose up -d db` + `./gradlew spotlessApply && ./gradlew build` verde
