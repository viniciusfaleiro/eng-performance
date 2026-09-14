## Why

Hoje só existem dois caminhos para trocar uma senha: o usuário autenticado informando a senha
atual (`PUT /api/auth/password`) ou um admin digitando a nova senha pela tela Admin → Usuários.
Quem esquece a senha fica **travado fora da plataforma** e depende de pedir a um admin — que
passa a conhecer a senha do outro. Não há nenhum canal de e-mail configurado no produto, então
qualquer fluxo de auto-serviço precisa trazer junto a configuração de envio.

## What Changes

- **NEW** — auto-serviço "Esqueci minha senha" na tela de login: o usuário informa o e-mail,
  recebe uma mensagem com um link contendo um token de uso único e define a nova senha por ele,
  sem nunca revelar a senha a terceiros.
- **NEW** — token de reset com hash em repouso: só o **hash** do token é persistido (o valor cru
  existe apenas no e-mail), com validade curta, **uso único** e invalidação dos demais tokens
  pendentes da conta assim que um é emitido ou consumido.
- **NEW** — resposta **neutra** ao pedido de reset: a API responde igual para e-mail existente,
  inexistente ou desabilitado, para não virar um oráculo de enumeração de contas. Contas
  `disabled` nunca recebem e-mail.
- **NEW** — limite de emissão por conta e por janela de tempo, para que o endpoint público não
  vire vetor de flood de e-mail contra um endereço.
- **NEW** — configuração de **servidor de e-mail (SMTP)** no Admin, como nova aba "E-mail", ao
  lado de "Integração ADO" e "Convenção de IA": host, porta, TLS, usuário, senha, remetente e
  URL base da aplicação (usada para montar o link), com botão **"enviar e-mail de teste"**.
  A senha SMTP é **write-only** na API (nunca retornada, mascarada na tela) e cifrada em repouso.
- **NEW** — sem SMTP configurado, um remetente de **log** assume: o link de reset sai no log da
  aplicação, para o fluxo continuar exercitável em dev/local sem servidor de e-mail — coerente
  com o seeder idempotente de fixtures.
- As rotas do fluxo (`/api/auth/password-reset/**`) entram na **allowlist pública** do
  `AuthTokenFilter`, hoje restrita a `/api/auth/login`.
- O botão "Redefinir senha" do Admin **permanece como está** (admin define a senha direto).

## Capabilities

### New Capabilities
- `password-reset`: ciclo de vida do reset de senha por token — pedido pelo e-mail, emissão e
  hash do token, validade/uso único/limite de emissão, consumo do token com troca de senha, e a
  resposta neutra que evita enumeração de contas.
- `email-delivery`: envio de e-mail transacional pela plataforma — configuração SMTP persistida
  e editável no Admin (segredo write-only), envio de e-mail de teste, e o remetente de log como
  fallback quando não há SMTP configurado.

### Modified Capabilities
- `authentication`: o conjunto de rotas públicas deixa de ser apenas o login e passa a incluir o
  pedido e a confirmação de reset; fica explícito que uma senha trocada por reset invalida a
  senha anterior para novos logins.

## Non-goals

- **Não** revogar sessões JWT já emitidas: o token de sessão é stateless (TTL de 12h, `JWT_TTL`),
  e revogação exigiria versionar o token por conta — fora do escopo desta change.
- **Não** unificar com o convite de conta nova (status `invited`) nem trocar o "Redefinir senha"
  do Admin por envio de link; ambos ficam como estão.
- **Não** introduzir segundo fator, captcha, magic-link de login sem senha, nem política de
  complexidade/expiração de senha.
- **Não** construir fila, retry ou caixa de saída persistente de e-mail; o envio é direto e uma
  falha vira erro registrado, não reprocessamento.
- **Não** criar templates de e-mail configuráveis pelo usuário nem outros e-mails transacionais
  (notificação de métrica, digest); só o reset e o e-mail de teste.
- **Não** mexer em métricas: nada aqui toca o motor on-read, `raw_event` ou os dashboards DORA /
  Fluxo / IA do PRD (`docs/initial-spec.md`). A change fica confinada ao domínio de contas, que o
  PRD trata em "Autenticação & contas de usuário".

## Impact

- **Specs**: novas `openspec/specs/password-reset` e `openspec/specs/email-delivery`; delta em
  `openspec/specs/authentication`.
- **domain**: novo `PasswordResetToken` (regras de validade/consumo) e `SmtpSettings` em
  `domain/config`, ao lado de `AdoIntegration`/`AiConvention`.
- **application**: `PasswordResetService` + `PasswordResetUseCase`; portas de saída
  `PasswordResetTokenPort`, `EmailSenderPort`, `TokenGenerator` e `Clock` já disponível no
  padrão do motor; `PlatformConfigPort` ganha get/save das configurações de SMTP.
- **adapter-out-persistence**: migration `V6` com a tabela `password_reset_token` e a tabela (ou
  colunas) de SMTP; entidades JPA e adaptadores correspondentes.
- **Novo adapter de saída** `adapter-out-email` (SMTP via `spring-boot-starter-mail`) — a regra
  do ArchUnit "só o `adapter-out-ado` fala HTTP" permanece válida, pois SMTP não é `java.net.http`;
  o novo módulo entra no `settings.gradle.kts` e no composition root.
- **adapter-in-web**: `AuthController` ganha `POST /api/auth/password-reset` e
  `POST /api/auth/password-reset/confirm`; `ConfigController` ganha o CRUD de SMTP e o envio de
  teste; `AuthTokenFilter` amplia a allowlist pública; `docs/api/openapi.yaml` atualizado.
- **UI** (`static/index.html`): link "Esqueci minha senha" + tela de nova senha por token (rota
  pública, antes do login), e a aba "E-mail" no Admin.
- **Dependências**: `spring-boot-starter-mail` no novo adapter; nenhum CDN, nenhum build de front.
