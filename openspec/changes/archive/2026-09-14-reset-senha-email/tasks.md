## 1. Domínio (puro, sem framework)

- [x] 1.1 Criar `domain/account/PasswordResetToken` (record com `id`, `accountId`, `tokenHash`, `createdAt`, `expiresAt`, `consumedAt`), com invariantes de campo obrigatório e `expiresAt` posterior a `createdAt`, e os predicados `isConsumed()`, `isExpired(Instant)` e `isUsableAt(Instant)`, mais `consumedAt(Instant)` retornando cópia.
- [x] 1.2 Criar `domain/config/SmtpSettings` (record com `enabled`, `host`, `port`, `transport`, `username`, `password`, `fromAddress`, `fromName`, `appBaseUrl`) no padrão de `AiConvention`: `Text.required/optional`, porta em 1..65535, `fromAddress` com `@` e `appBaseUrl` sem barra final; novo enum `MailTransport` (`NONE`/`STARTTLS`/`SSL`).
- [x] 1.3 Testes unitários de `PasswordResetToken` (usável, expirado, consumido, limites de prazo) e de `SmtpSettings` (validações e normalização), cobrindo os ramos para sustentar o piso de 70%/60%.

## 2. Portas e serviço de aplicação

- [x] 2.1 Criar as portas de saída `PasswordResetTokenPort` (`save`, `findByTokenHash`, `findPendingByAccount`, `consumeAllPending`, `countIssuedSince`), `EmailSenderPort` (`send(to, subject, body)` + `EmailDeliveryException`) e `SecureTokenGenerator` (`generate()` devolvendo o token cru, `hash(String)` devolvendo o hash).
- [x] 2.2 Estender `PlatformConfigPort` com `getSmtpSettings()`/`saveSmtpSettings(...)` e `PlatformConfigUseCase` com a operação equivalente, mantendo a assinatura write-only do segredo (valor ausente preserva o guardado).
- [x] 2.3 Criar `PasswordResetUseCase` (inbound) com `requestReset(String email)` e `confirmReset(String token, String newPassword)`.
- [x] 2.4 Implementar `PasswordResetService`: normaliza o e-mail, resolve a conta, trata inexistente/`DISABLED` como no-op silencioso, aplica o teto de 3 emissões em 15 min via `countIssuedSince`, invalida pendentes, gera token + hash, persiste e envia o e-mail com o link `{appBaseUrl}/?reset=<token>`; `Clock` injetado, como em `MetricsService`.
- [x] 2.5 Implementar o consumo: busca por hash, recusa token inexistente/expirado/consumido com um erro único e indistinguível, valida senha nova não-branca, grava o hash novo em `UserAccountRepositoryPort` e marca o token consumido na mesma unidade de trabalho.
- [x] 2.6 Testes unitários de `PasswordResetService` com fakes das portas: caminho feliz, e-mail desconhecido, conta desabilitada, teto de emissão, supersessão do pendente, replay, expirado e senha em branco — verificando que nenhum caminho revela a existência da conta.

## 3. Persistência (PostgreSQL)

- [x] 3.1 Migration `V6__password_reset_email.sql`: tabela `password_reset_token` (índice único em `token_hash`; índice em `account_id, created_at`) e `smtp_settings` (linha singleton `default`, `enabled = false`).
- [x] 3.2 Entidades JPA `PasswordResetTokenEntity` e `SmtpSettingsEntity` + repositórios Spring Data, no padrão das entidades existentes do módulo.
- [x] 3.3 Implementar `JpaPasswordResetTokenRepository` (adapter da porta) e estender `JpaPlatformConfigRepository` com o get/save de SMTP, preservando o segredo quando o valor chega nulo.
- [x] 3.4 Cifrar o segredo: utilitário AES-256-GCM com chave Base64 da env `CONFIG_ENCRYPTION_KEY` (IV aleatório por gravação, guardado com o ciphertext); sem a chave, salvar senha de SMTP falha com mensagem explícita, e decifragem inválida é tratada como "sem senha configurada" com log.
- [x] 3.5 Teste de integração com Testcontainers: ida-e-volta do token (emissão, busca por hash, consumo, contagem por janela) e do `smtp_settings` (segredo cifrado no banco, preservado em save sem senha).

## 4. Adapter de e-mail (novo módulo)

- [x] 4.1 Criar `adapters/out-email` com `build.gradle.kts` (depende de `:domain`, `:application`, `spring-boot-starter-mail`) e registrá-lo no `settings.gradle.kts` como `:adapter-out-email`, mapeando o `projectDir`.
- [x] 4.2 Adicionar o módulo às dependências de `:bootstrap` e de `:architecture-tests` — sem isso o ArchUnit não enxerga o pacote novo.
- [x] 4.3 Implementar `SmtpEmailSender`: monta o `JavaMailSender` a partir de `SmtpSettings` (host/porta/transporte/credenciais), com timeouts curtos de conexão e leitura, e propaga falha como `EmailDeliveryException`.
- [x] 4.4 Implementar `LogEmailSender` (registra destinatário, assunto e corpo) e `RoutingEmailSender`, que consulta `PlatformConfigPort` **a cada envio** e escolhe SMTP quando há configuração habilitada com host, senão log — falha de SMTP configurado nunca cai no log.
- [x] 4.5 Testes do roteamento (com SMTP habilitado vs. ausente) e do mapeamento de `SmtpSettings` para as propriedades do `JavaMailSender`.

## 5. Web (API + filtro)

- [x] 5.1 Adicionar `POST /api/auth/password-reset` e `POST /api/auth/password-reset/confirm` ao `AuthController`, com DTOs próprios; o pedido responde **202 sem corpo** em todos os casos e a confirmação responde 204, ou 400 para token inválido/expirado/consumido e senha em branco.
- [x] 5.2 Ampliar a allowlist pública do `AuthTokenFilter` para as duas rotas novas, mantendo todo o resto de `/api/**` fechado, e cobrir com teste.
- [x] 5.3 Adicionar ao `ConfigController` o `GET`/`PUT` de `/api/admin/email` (senha write-only, resposta com `passwordSet`) e o `POST /api/admin/email/test`, que reporta sucesso ou o erro da entrega.
- [x] 5.4 Testes de web (MockMvc): resposta idêntica para e-mail conhecido/desconhecido/desabilitado, rotas públicas passando sem token, `/api/admin/email` exigindo admin, e o `GET` nunca devolvendo a senha.
- [x] 5.5 Atualizar `docs/api/openapi.yaml` com os quatro endpoints novos e seus schemas.

## 6. Composition root

- [x] 6.1 Criar/estender o wiring (`AuthWiring` ou novo `PasswordResetWiring`) ligando `PasswordResetService` às portas, com `Clock` e `SecureTokenGenerator` (implementação com `SecureRandom` + SHA-256) declarados como beans.
- [x] 6.2 Registrar os beans do `adapter-out-email` (SMTP, log e roteador) no bootstrap, garantindo que apenas o roteador seja injetado na aplicação.

## 7. UI (SPA self-contained)

- [x] 7.1 Adicionar o link "Esqueci minha senha" na tela de login e o formulário de pedido (campo e-mail), com mensagem de confirmação neutra que não afirma se o e-mail existe.
- [x] 7.2 Ler `location.search` no boot: havendo `reset`, exibir a tela "Definir nova senha" no lugar do overlay de login; ao concluir, limpar o token da URL com `history.replaceState` e voltar ao login.
- [x] 7.3 Adicionar a aba "E-mail" no Admin (ao lado de Integração ADO e Convenção de IA) com o formulário de SMTP, senha mascarada, botão "enviar e-mail de teste" e o aviso visível de modo log quando não houver SMTP habilitado.
- [x] 7.4 Traduzir as mensagens de erro novas no mapa de erros do SPA (token inválido/expirado, senha obrigatória, falha de envio) e conferir a paridade visual com `prototype/index.html`.

## 8. Fechamento

- [x] 8.1 Documentar no README a env `CONFIG_ENCRYPTION_KEY`, a aba E-mail e o comportamento de modo log; conferir que `CLAUDE.md` segue correto com o módulo novo.
- [x] 8.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL** (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo e ArchUnit verdes).
