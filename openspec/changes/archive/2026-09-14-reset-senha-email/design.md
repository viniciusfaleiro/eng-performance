## Context

A autenticação atual (`authentication`, slice S2) é stateless: `AuthService` valida a senha contra
o hash BCrypt, `JwtTokenService` emite um JWT com TTL de 12h (`JWT_TTL`) e o `AuthTokenFilter`
trata **apenas** `/api/auth/login` como rota pública — todo o resto de `/api/**` exige token.
Trocar a senha exige conhecer a senha atual (`PUT /api/auth/password`) ou um admin digitando a
nova senha na tela Admin → Usuários. Quem esquece a senha não tem saída autônoma.

A plataforma **nunca enviou e-mail**: não há dependência de mail, remetente configurado nem URL
base conhecida pelo backend. O reset por token e a configuração de envio chegam juntos.

Restrições que moldam o desenho:

- **Hexagonal + ArchUnit**: `domain` sem framework, `application` sem Spring, e a regra "só o
  `adapter.outbound.ado` depende de `java.net.http`". SMTP não usa `java.net.http`, então um novo
  adapter de saída de e-mail não conflita com a regra existente.
- **Nada em memória**: token e configuração de SMTP vivem no PostgreSQL, via Flyway + JPA atrás
  das portas.
- **Piso de cobertura** 70% linha / 60% branch em `domain` + `application`: a lógica de validade,
  uso único e limite de emissão precisa morar nessas camadas, testável sem banco e sem SMTP.
- **UI self-contained**: a SPA (`static/index.html`) é uma página só, sem roteamento por hash; a
  tela de login é um overlay (`#login`) exibido antes de qualquer chamada autenticada.
- **`Clock` injetado** já é o padrão do repo (`MetricsService`, `AdoSyncService`) — validade e
  janela de limite usam o mesmo mecanismo, mantendo os testes determinísticos.

## Goals / Non-Goals

**Goals:**

- Dar ao usuário um caminho autônomo de recuperação que não exponha a senha a nenhum terceiro,
  nem mesmo ao admin.
- Não transformar o endpoint público num oráculo de enumeração de contas nem num amplificador de
  e-mail contra um endereço.
- Manter o segredo de SMTP fora de qualquer resposta de API e fora do banco em claro.
- Deixar o fluxo exercitável em dev/local sem servidor de e-mail, sem código condicional espalhado.
- Manter `domain`/`application` livres de Spring, JavaMail e SQL.

**Non-Goals:**

- Revogar sessões JWT já emitidas (exigiria versionar o token por conta).
- Convite de conta nova (`invited`), segundo fator, captcha, magic-link e política de senha.
- Fila/retry/outbox de e-mail, templates configuráveis e outros e-mails transacionais.

## Decisions

### 1. Token opaco de 256 bits, guardado como SHA-256

Gerar 32 bytes de `SecureRandom` e transportar em Base64 URL-safe (43 caracteres). No banco fica
apenas o **SHA-256** do token; a confirmação re-hasheia o valor recebido e busca pelo hash (índice
único). Vazamento de backup ou de log de banco não dá tokens utilizáveis.

*Por que não BCrypt* (como na senha): BCrypt é deliberadamente lento e **salgado por linha**, o
que impediria a busca direta pelo hash — teríamos de varrer tokens candidatos. A lentidão existe
para proteger segredos de baixa entropia escolhidos por humanos; um token de 256 bits aleatórios
não é atacável por força bruta offline, então o hash rápido e determinístico é a escolha certa.

*Por que não um JWT assinado e sem estado*: uso único e revogação exigem estado de qualquer forma;
um JWT só adicionaria tamanho ao link e a ilusão de não precisar de tabela.

### 2. Uso único, TTL de 60 min e supersessão dos pendentes

A tabela `password_reset_token` guarda `account_id`, `token_hash`, `created_at`, `expires_at`,
`consumed_at`. Um token é válido quando não foi consumido e `now < expires_at`. Ao consumir,
grava-se `consumed_at` na **mesma transação** da troca do hash da senha — sem isso, uma falha no
meio deixaria o token gasto sem senha nova (ou o contrário).

Emitir um token novo **invalida os pendentes** da conta (marca `consumed_at`, sem gastar a troca):
quem pede duas vezes usa o último link que recebeu, e o e-mail antigo — que pode ter vazado em um
encaminhamento — deixa de servir. TTL de 60 min equilibra o tempo real de o e-mail chegar e ser
aberto contra a janela de exposição do link na caixa postal.

### 3. Resposta neutra e limite por conta

`POST /api/auth/password-reset` responde **202 Accepted** com corpo vazio em todos os casos:
e-mail existente, inexistente, desabilitado ou limite estourado. Nada na resposta — status, corpo
ou cabeçalho — distingue os casos; o único efeito observável é o e-mail, que só chega a contas
`active`/`invited` existentes. Contas `disabled` são tratadas como inexistentes, coerente com o
login, que já recusa `disabled` sem dizer o motivo.

O limite é de **3 emissões por conta em 15 minutos**, contadas por `created_at` na própria tabela
de tokens — sem store adicional. É um limite por **conta**, não por IP: protege a caixa postal da
vítima, que é o alvo real; limitar por IP em um app atrás de proxy exigiria confiar em
`X-Forwarded-For`, o que fica fora do escopo.

Excesso não vira 429 — isso reintroduziria o oráculo. A tentativa é apenas descartada e registrada
em log no servidor.

### 4. Configuração de SMTP como terceira config singleton

`smtp_settings` segue o padrão de `ado_integration` e `ai_convention`: linha única com `id`
fixo, lida/gravada por `PlatformConfigPort`. Campos: `host`, `port`, `username`,
`password_secret`, `from_address`, `from_name`, `tls` (`none`/`starttls`/`ssl`), `app_base_url`
e `enabled`. O `app_base_url` é indispensável — o backend não pode inferir a URL pública a partir
da requisição (proxy, host header) sem criar um vetor de host-header injection no link do e-mail.

A senha SMTP é **write-only**: `GET` devolve `passwordSet: true|false`, nunca o valor; um `PUT`
sem o campo preserva o segredo guardado, e só o substitui quando um valor novo é enviado.

### 5. Segredo cifrado com AES-GCM e chave de ambiente

`password_secret` é cifrado com **AES-256-GCM**, chave vinda da env `CONFIG_ENCRYPTION_KEY`
(32 bytes em Base64); IV aleatório por gravação, guardado junto do ciphertext. A cifragem mora no
`adapter-out-persistence` — domínio e aplicação só veem o valor em claro através da porta.

Sem a env, salvar uma senha de SMTP é **recusado** com uma mensagem explícita ("defina
`CONFIG_ENCRYPTION_KEY`"), em vez de gravar em claro silenciosamente. Dev/local não precisa da
chave porque o fallback de log não usa SMTP.

*Alternativa considerada*: manter só a senha em variável de ambiente (`MAIL_PASSWORD`), com o
resto no Admin. Evita cifragem própria, mas parte a configuração em dois lugares e volta a exigir
restart para rotacionar o segredo — descartada em favor da tela única já escolhida.

### 6. `EmailSenderPort` com roteamento em runtime, não em bootstrap

A porta é uma só (`EmailSenderPort.send(to, subject, body)`), com dois adaptadores no novo módulo
`adapter-out-email`: `SmtpEmailSender` (Spring Mail, constrói o `JavaMailSender` a partir da
configuração persistida) e `LogEmailSender` (registra destinatário, assunto e corpo no log).

A escolha acontece **a cada envio**, num `RoutingEmailSender` que lê `PlatformConfigPort`: se há
SMTP habilitado e com host, usa SMTP; senão, log. Escolher no composition root congelaria a
decisão no boot, e o admin que configurasse o SMTP pela tela continuaria a "enviar" para o log até
reiniciar o app.

O `LogEmailSender` é **fallback de ausência de configuração**, não de falha: SMTP configurado que
falha no envio propaga o erro (visível no e-mail de teste), em vez de escorregar para o log e dar
a impressão de que a mensagem saiu.

### 7. Novo módulo `adapter-out-email`, e não código de e-mail no in-web

`spring-boot-starter-mail` entra em um módulo próprio (`adapters/out-email`), registrado no
`settings.gradle.kts`, no `bootstrap` e na lista de projetos dos `architecture-tests` — sem isso
o ArchUnit não enxerga as classes novas e a fronteira ficaria sem guarda. O pacote
`com.engperf.adapter.outbound.email` já cai nas regras de camada existentes, que são por pacote.

### 8. Link entra na SPA por query string

O e-mail leva `{app_base_url}/?reset=<token>`. A SPA hoje não tem roteamento; no boot ela passa a
ler `location.search`, e havendo `reset` mostra a tela "Definir nova senha" no lugar do overlay de
login. Depois de confirmar, o token é **removido da URL** com `history.replaceState` e o usuário
cai no login — evitando que o token fique no histórico do navegador e vaze por `Referer`.

O token não é validado antes do envio da nova senha: a tela pede a senha e só então chama
`POST /api/auth/password-reset/confirm`, que responde 400 para token inválido, expirado ou já
usado. Uma pré-validação seria um segundo endpoint público confirmando a existência do token, sem
ganho real de UX.

### 9. Onde mora a lógica

`domain`: `PasswordResetToken` (validade, consumo, invariantes de prazo) e `SmtpSettings`
(validação de host/porta/remetente), ambos puros e cobertos por teste unitário.
`application`: `PasswordResetService` orquestra conta → token → e-mail, aplica o limite de
emissão, monta a mensagem a partir do `app_base_url` e nunca lança erro que diferencie e-mails.
Portas novas: `PasswordResetTokenPort`, `EmailSenderPort`, `SecureTokenGenerator` — as três
implementadas nos adapters, mantendo `application` sem `SecureRandom`, JavaMail ou SQL.

## Risks / Trade-offs

- **Sessão ativa sobrevive ao reset (até 12h)** → documentado como non-goal; quem reseta por
  suspeita de comprometimento deve pedir ao admin que desative a conta, o que barra o login na
  hora. Versionar o token de sessão fica registrado para uma change futura.
- **Link no log em dev pode virar caminho de produção** se alguém subir sem SMTP → o Admin mostra
  o estado ("e-mail em modo log, nenhum SMTP configurado") e a aba de e-mail fica sinalizada
  enquanto não houver SMTP habilitado.
- **`CONFIG_ENCRYPTION_KEY` perdida** torna o segredo de SMTP ilegível → a falha é de decifragem
  no boot/uso, tratada como "SMTP não configurado" com log explícito; a recuperação é redigitar a
  senha na tela. Documentar a env no README.
- **E-mail entregue em spam** faz o usuário achar que o fluxo quebrou → o botão "enviar e-mail de
  teste" existe justamente para o admin validar entrega e remetente antes de anunciar o recurso.
- **Limite por conta não impede varredura ampla** de muitos e-mails diferentes por um atacante →
  mitigado pela resposta neutra (a varredura não retorna informação) e pelo fato de cada endereço
  só receber 3 mensagens por janela.
- **Envio síncrono na requisição** deixa o 202 dependente do tempo do SMTP → aceitável no volume
  desta plataforma (interno, baixa frequência); mitigado por timeout curto de conexão/leitura, com
  a falha registrada sem alterar a resposta.

## Migration Plan

1. Migration `V6__password_reset_email.sql`: cria `password_reset_token` (índice único em
   `token_hash`, índice em `account_id, created_at`) e `smtp_settings` com a linha `default`
   desabilitada. Ambas são tabelas novas — nenhuma coluna existente muda, então não há
   reescrita de dados nem janela de indisponibilidade.
2. Deploy do app; sem `CONFIG_ENCRYPTION_KEY` e sem SMTP, a plataforma sobe igual a hoje e o
   fluxo opera em modo log.
3. Admin define `CONFIG_ENCRYPTION_KEY` no ambiente, preenche a aba E-mail e valida com o envio
   de teste; o recurso passa a enviar de verdade sem novo deploy.
4. **Rollback**: desligar `enabled` na aba E-mail volta ao modo log; reverter o binário deixa as
   duas tabelas órfãs e inertes (nenhum código antigo as lê), o que torna o rollback seguro sem
   desfazer a migration.

## Open Questions

- Texto e assinatura do e-mail (remetente exibido, menção à empresa) — assumido um texto simples
  em português, sem HTML, até que o usuário defina a redação.
- Se o TTL de 60 min e o limite de 3/15 min devem virar configuração no Admin — assumidos fixos
  nesta change, para não expandir a superfície da tela.
