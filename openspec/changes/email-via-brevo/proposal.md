## Why

A rede do ambiente de homologação bloqueia SMTP de saída de forma silenciosa: a conexão TCP para
`smtp.gmail.com:587` é estabelecida, mas o servidor nunca envia o banner `220` e o envio morre no
timeout. HTTPS na 443, ao contrário, funciona do mesmo container — é o canal que a sincronização do
Azure DevOps já usa. Enquanto isso, o reset de senha por e-mail (capacidade `password-reset`) só
existe em modo log, o que impede validar o fluxo com usuários reais.

Trocar o transporte por uma **API HTTPS** resolve a causa sem depender de exceção de firewall: o
pedido de liberação, se ainda for necessário, passa a ser de um domínio em HTTPS — a mesma natureza
da regra que já existe para o Azure DevOps.

## What Changes

- Novo **provedor de envio via API HTTPS do Brevo**, como terceira opção ao lado do modo log e do
  SMTP já existentes.
- A tela **Admin → E-mail** ganha a escolha explícita do provedor (`log` / `smtp` / `brevo`) e os
  campos do Brevo (chave de API e remetente). Os campos irrelevantes para o provedor escolhido não
  são exigidos.
- A **chave de API do Brevo é tratada como segredo**, com as mesmas garantias já aplicadas à senha
  SMTP: write-only na API, cifrada em repouso, preservada quando omitida no save.
- O **e-mail de teste** e todo o envio transacional passam a usar o provedor selecionado, relatando
  o erro do provedor quando a entrega falha.
- O `SmtpEmailSender` **continua funcionando** e selecionável — o bloqueio é característica desta
  rede, não uma decisão de arquitetura.
- A regra de ArchUnit "só o `adapter-out-ado` fala HTTP" é ampliada para admitir o
  `adapter-out-email`. A fronteira que importa é *adapters de saída falam com o mundo, domínio e
  aplicação não* — e ela continua imposta.

## Capabilities

### New Capabilities

Nenhuma. O envio de e-mail já é uma capacidade existente; muda o transporte, não o que o sistema
faz por quem usa.

### Modified Capabilities

- `email-delivery`: a configuração deixa de ser "o servidor SMTP" e passa a ser "o provedor de
  envio", com um provedor HTTPS adicional; o segredo protegido deixa de ser só a senha SMTP e passa
  a incluir a chave de API; o fallback para log passa a valer quando *nenhum* provedor está
  configurado, não só quando falta SMTP.

## Non-goals

- **Não** remover nem depreciar o SMTP. Ele permanece como opção de primeira classe.
- **Não** autenticar domínio (DKIM/SPF) no Brevo. O remetente é um endereço avulso verificado por
  código, que já permite enviar a qualquer destinatário; autenticar o domínio da empresa melhora
  entregabilidade e é passo de produção, fora deste change.
- **Não** adicionar outros provedores (SES, SendGrid, Mailgun). O ponto de extensão fica aberto,
  mas só o Brevo é implementado.
- **Não** mudar o conteúdo, o idioma ou o gatilho dos e-mails. O corpo do reset de senha
  permanece exatamente como está.
- **Não** tocar no motor de métricas nem em nada do PRD (`docs/initial-spec.md`) — e-mail é
  infraestrutura de conta, não medição.
- **Não** implementar filas, retentativa ou rastreio de entrega. O envio segue síncrono, como hoje.

## Impact

- **`domain`**: a configuração de e-mail passa a nomear um provedor e a carregar as credenciais do
  Brevo, com as validações correspondentes.
- **`application`**: o port de saída de e-mail ganha a noção de provedor selecionado; o use case de
  configuração passa a tratar dois segredos em vez de um.
- **`adapter-out-email`**: novo sender HTTPS usando `java.net.http`, ao lado do SMTP e do log; o
  roteador escolhe pelo provedor configurado.
- **`adapter-out-persistence`**: a coluna do novo segredo, cifrada pelo `SecretCipher` já existente
  (migração Flyway).
- **`adapter-in-web`**: os campos novos na tela de Admin → E-mail e no contrato REST da
  configuração.
- **`architecture-tests`**: a regra de HTTP passa a listar os dois adapters de saída que falam com
  a rede.
- **Operação**: continua exigindo `CONFIG_ENCRYPTION_KEY` — agora também para a chave de API.
- **Dependências**: nenhuma nova. `java.net.http` é da biblioteca padrão, como no adapter do ADO.
