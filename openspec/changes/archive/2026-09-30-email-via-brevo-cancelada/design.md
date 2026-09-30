## Context

O envio transacional hoje tem dois caminhos, escolhidos implicitamente pelo `RoutingEmailSender`:
se `SmtpSettings.isUsable()` (habilitado + host + remetente), envia por SMTP; caso contrário,
escreve no log. A única coisa que consome e-mail é o reset de senha.

A rede de homologação torna o caminho SMTP inviável: a conexão com `smtp.gmail.com:587` é
estabelecida, o servidor nunca envia o banner `220`, e o `JavaMailSender` estoura o timeout de 5s.
Assinatura de firewall que aceita o SYN e descarta o payload. HTTPS na 443 funciona do mesmo
container — verificado com `curl` na rede `engperf-net`, mesma rota que a ingestão do Azure DevOps
usa em produção de dados.

Restrições que moldam o desenho:

- A regra de ArchUnit `onlyAdoAdapterTalksHttp` proíbe qualquer pacote fora de
  `com.engperf.adapter.outbound.ado..` de depender de `java.net.http..`.
- `domain` e `application` não conhecem framework; `application` não conhece Spring.
- Segredos de configuração são cifrados pelo `SecretCipher` (AES-256 com `CONFIG_ENCRYPTION_KEY`),
  nunca retornados por endpoint.
- Os pisos de cobertura do JaCoCo (70% linha / 60% branch) valem para `domain` + `application`.

## Goals / Non-Goals

**Goals:**

- Entregar e-mail transacional por um canal que a rede do HML permite, sem exceção de firewall.
- Tornar a escolha do transporte **explícita** na configuração, em vez de inferida.
- Aplicar à chave de API do Brevo exatamente as garantias que a senha SMTP já tem.
- Manter o SMTP como opção de primeira classe.

**Non-Goals:**

- Verificação de domínio / DKIM no Brevo, filas, retentativa, rastreio de entrega, webhooks de
  bounce, e outros provedores. Ver a seção Non-goals do `proposal.md`.

## Decisions

### 1. O provedor é escolhido explicitamente, não inferido do preenchimento

`EmailSettings` passa a carregar um `EmailProvider` (`LOG` / `SMTP` / `BREVO`), e o
`RoutingEmailSender` despacha por ele.

Hoje o modo log é consequência de `isUsable()` — um host em branco faz o sistema silenciosamente
parar de enviar e-mail. Isso já é uma armadilha com dois caminhos; com três, o número de estados a
inferir cresce e a inferência vira adivinhação (chave de API preenchida mas host também: qual
vence?). Uma escolha explícita também deixa o modo log ser uma decisão legítima — "estou testando,
não quero enviar" — em vez de um acidente.

*Alternativa considerada:* manter a inferência e ordenar por precedência (Brevo > SMTP > log).
Rejeitada: economiza um campo na tela e paga com um comportamento que ninguém consegue prever
lendo a configuração.

O campo `enabled` existente vira redundante e é absorvido: `provider = LOG` **é** o desligado.
Manter os dois permitiria o estado incoerente "provedor BREVO, enabled false", que a tela teria
que explicar.

### 2. Estender a tabela existente, não criar outra

`V7` adiciona `provider` e `brevo_api_key_ciphertext` a `smtp_settings`, com backfill:
`provider = 'SMTP'` onde `enabled` era verdadeiro, `'LOG'` no resto — preservando o comportamento
observável de qualquer ambiente já configurado. A coluna `enabled` é removida na mesma migração,
já que o provedor a substitui.

`from_address`, `from_name` e `app_base_url` são compartilhados entre provedores: são propriedades
da *mensagem*, não do transporte. Duplicá-los por provedor criaria a pergunta "qual remetente vale
agora?".

O nome da tabela fica `smtp_settings` apesar de não descrever mais o conteúdo. Renomear custaria
uma migração de nome propagada por entidade, repositório e testes, sem alterar comportamento — e o
repositório já convive com esse tipo de defasagem. Registrado aqui para não parecer descuido.

*Alternativa considerada:* tabela `email_settings` nova com migração de dados. Rejeitada pelo
custo/benefício; o gate que importa (a senha SMTP continua cifrada e legível) não muda.

### 3. `java.net.http.HttpClient` direto, e a regra de ArchUnit é ampliada

Sem dependência nova: o `HttpClient` da biblioteca padrão já é o que o adapter do ADO usa, o corpo
é um JSON de quatro campos e a resposta é lida por Jackson, que já está no classpath.

A regra `onlyAdoAdapterTalksHttp` passa a permitir `com.engperf.adapter.outbound.email..` junto do
ADO. A intenção original da regra não era "só o ADO pode falar HTTP" como fim em si — era impedir
que domínio, aplicação e adapters de entrada abrissem conexão. Essa fronteira continua imposta; o
que muda é que existem dois adapters de saída falando com a rede em vez de um. A regra é
reescrita em termos de uma lista nomeada, para que a próxima adição seja uma linha e não uma
reinterpretação.

*Alternativa considerada:* SDK oficial do Brevo. Rejeitada: uma dependência transitiva nova para
uma chamada `POST` de um endpoint só, num repo que evita dependências (`sem CDN`, `sem build de
frontend`).

### 4. Falha do provedor não cai para o log

Mantém a regra que a spec de `email-delivery` já fixa para SMTP: o fallback de log vale para
*ausência* de configuração, não para *falha* de entrega. Um 4xx do Brevo vira
`EmailDeliveryException` com a mensagem do provedor, exatamente como um `MailException` vira hoje.

Isso importa no reset de senha: registrar no log um e-mail que o provedor recusou faria o sistema
relatar sucesso para um usuário que nunca receberá o link.

### 5. Timeouts iguais aos do SMTP (5s)

Mesma constante, mesmo motivo: o envio é síncrono dentro de uma requisição HTTP do admin ou do
fluxo de reset. É o timeout que transformou o bloqueio silencioso da rede em erro visível — vale
preservá-lo.

### 6. Teste sem rede: `HttpClient` injetado e mockado

O `BrevoEmailSender` recebe o `HttpClient` por construtor, como o `HttpAdoRestClient` já faz, e os
testes injetam um mock que devolve status e corpo. Nenhum teste abre socket — o que é
particularmente relevante aqui, já que a rede onde isso roda é justamente o problema.

## Risks / Trade-offs

- **A chave de API é um segredo de portador — vaza e qualquer um envia e-mail em nome do remetente**
  → mesmo tratamento da senha SMTP: cifrada em repouso, write-only na API, nunca em log. O
  `BrevoEmailSender` não pode incluir o header `api-key` em mensagem de exceção; a mensagem
  de erro carrega só o corpo da resposta do provedor.

- **Remover `enabled` é mudança de esquema destrutiva** → o backfill deriva o provedor a partir
  dela antes de removê-la, na mesma migração. Um rollback do app para a versão anterior sem
  rollback do banco quebraria (a coluna some); ver Migration Plan.

- **Remetente verificado por código não tem DKIM próprio** → o envio funciona para qualquer
  destinatário, mas a mensagem sai assinada pelo domínio do Brevo, não pelo da empresa. Em volume
  transacional baixo isso entrega; a chance de cair em spam no Outlook corporativo é real. Mitigação
  de produção: autenticar o domínio da empresa, fora deste change.

- **Plano gratuito limita 300 e-mails/dia** → folgado para reset de senha; viraria restrição se um
  dia o produto enviar relatório periódico. Não é o caso hoje.

- **Um terceiro caminho de envio é mais superfície para divergir** → os três implementam a mesma
  interface e o roteador é um `switch` sobre o enum; a spec cobre o comportamento comum (falha não
  cai para log) uma vez só, valendo para todos.

## Migration Plan

1. `V7` adiciona as colunas, faz o backfill do provedor a partir de `enabled` e remove `enabled`.
2. Deploy do app já lendo `provider`. Ambientes existentes continuam no comportamento anterior:
   quem tinha SMTP habilitado segue em `SMTP`, o resto em `LOG`.
3. No HML: escolher `Brevo`, salvar a chave de API, enviar e-mail de teste.

**Rollback:** reverter o app exige reverter a migração (a versão anterior lê `enabled`). Como o
conteúdo da tabela é uma linha de configuração, o caminho prático é restaurar o `enabled` por SQL a
partir do `provider` antes de voltar a versão — mais barato que um `undo` versionado do Flyway para
uma tabela singleton.

## Open Questions

- O remetente definitivo de produção (`no-reply@<domínio da empresa>`) depende de acesso ao DNS
  corporativo para autenticar o domínio no Brevo. Fora deste change; não bloqueia o uso em
  homologação, já que o remetente verificado por código entrega para qualquer destinatário.
