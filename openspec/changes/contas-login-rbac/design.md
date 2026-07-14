## Context

O S1 entregou contas (email/senha BCrypt em `user_account`, via `PasswordHasher`)
e a estrutura (Pessoa↔Time↔Vertical com gestores). Falta autenticar e autorizar.
Requisito do usuário: **simples** — login na plataforma e permissões **derivadas
da estrutura**, com `admin`/`exec` explícitos. Sem SSO/OAuth externo, sem
multi-org. Domínio permanece sem framework; persistência PostgreSQL.

## Goals / Non-Goals

**Goals:**
- Login próprio (email/senha) com sessão durável (sobrevive a restart), logout,
  `/auth/me` com escopo, troca da própria senha.
- Política de autorização **pura no domínio**: dada a conta + a estrutura, produz
  o escopo visível; o web nega com 403 fora dele.
- Coaching-only preservado.

**Non-Goals:**
- Métricas/telas de dado real (S3+) — o enforcement incide sobre estrutura/admin.
- SSO/Entra/OAuth, reset de senha por email, papéis granulares além de admin/exec.

## Decisions

- **Token stateless assinado (JWT), sem session store.** No login, verifica a senha
  e emite um JWT curto (claims: accountId, role, personId) assinado com um segredo
  de config. Um filtro no `adapter-in-web` valida o token por request e popula um
  principal. *Por quê:* durável por construção (nada em memória, sobrevive a
  restart) e simples. *Alternativa:* sessão opaca em tabela — rejeitada por exigir
  store e limpeza; *Spring Security completo* — rejeitado por peso/config; usamos
  um filtro fino + o `PasswordHasher` (estendido com `matches`).
- **`AccessPolicy` no domínio (pura).** Função `scopeOf(account, structure)` →
  `AccessScope`. Regras: `admin` → tudo + config; `exec` → todas as verticais
  (leitura), sem config; senão deriva da Pessoa vinculada — gestor de vertical
  (vê a vertical agregada), gestor de time (vê o time + indivíduos), membro (vê
  o próprio + agregado do time). *Por quê:* é regra de negócio testável sem Spring.
- **`AccessScope` = predicados, não lista fixa.** Expõe `canView(nodeId)` e
  `canViewIndividual(personId)` (coaching-only). O web consulta o predicado.
- **Enforcement por interceptor no web.** Um `HandlerInterceptor` lê o `nodeId`/
  `personId` da request e chama o predicado do escopo; fora do escopo → 403;
  não autenticado → 401. Endpoints de config exigem `admin`.
- **Verificação de senha via porta.** Estende `PasswordHasher` com
  `boolean matches(raw, hash)` (BCrypt `matches` no adapter) — sem trafegar a
  senha nem expor o hash.
- **Frontend real.** A tela de login do protótipo passa a `POST /auth/login`,
  guarda o token e o envia (header `Authorization`); em 401, mostra erro. A árvore
  de navegação já vem filtrada pelo escopo do backend. Logar como `admin` mostra
  tudo (mantém paridade com o protótipo).

## Risks / Trade-offs

- **Segredo do JWT em config.** → Lido de env (`JWT_SECRET`) com default de dev;
  documentado. Sem segredo forte em produção o token é forjável (aceitável no MVP).
- **Sem revogação de token (stateless).** → TTL curto; logout é client-side
  (descarta o token). Revogação real fica para depois se necessário.
- **Paridade visual.** → Login como admin reproduz o protótipo (visão completa);
  o parity-test roda como admin. Escopos menores mudam o que aparece — coberto por
  testes de API, não pela paridade visual.
- **Escopo derivado x dados do S3.** → Aqui o escopo incide sobre estrutura/admin;
  quando as métricas chegarem (S3), reusam o mesmo `AccessScope`.

## Migration Plan

1. Estender `PasswordHasher` (matches) + adapter BCrypt.
2. Domínio: `AccessPolicy`/`AccessScope`; app: use-cases de auth + `TokenService`
   (porta) + adapter JWT; web: `/auth/*`, filtro de token, interceptor de escopo.
3. Filtrar `/structure/tree` e proteger os endpoints por escopo; config exige admin.
4. Wire no bootstrap; senha do seed continua BCrypt.
5. Frontend: login real + envio do token; parity-test como admin.
6. `./gradlew build` verde.

## Open Questions

- Token no header `Authorization: Bearer` (proposto) ou cookie HttpOnly? (Header é
  mais simples para o fetch do protótipo.)
- TTL do token (proposto: algumas horas) e se precisamos de refresh (proposto: não
  no MVP).
