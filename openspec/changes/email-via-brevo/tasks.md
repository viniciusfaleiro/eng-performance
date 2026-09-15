## 1. Domínio — o provedor entra no modelo

- [ ] 1.1 Criar o enum `EmailProvider` (`LOG`, `SMTP`, `BREVO`) em `domain/config`.
- [ ] 1.2 Evoluir `SmtpSettings` para carregar `provider` e `brevoApiKey`, removendo `enabled`
      (o provedor o substitui); validar que `provider` não é nulo e manter as validações atuais
      de porta, remetente e URL base.
- [ ] 1.3 Substituir `isUsable()` por algo que responda à pergunta certa por provedor — SMTP exige
      host e remetente, BREVO exige chave e remetente, LOG não exige nada.
- [ ] 1.4 Estender `withPassword(...)` com o equivalente para a chave de API (null preserva a
      atual), mantendo a semântica write-only que a spec exige.
- [ ] 1.5 Testes de domínio das regras acima, incluindo o caso "BREVO sem chave não é utilizável".

## 2. Aplicação — port e use case

- [ ] 2.1 Ajustar `PlatformConfigUseCase` para aceitar e preservar os dois segredos, sem jamais
      devolvê-los.
- [ ] 2.2 Ajustar `PlatformConfigPort` e os fakes de teste ao novo formato de settings.
- [ ] 2.3 Testes cobrindo: salvar sem chave preserva a anterior; ler nunca devolve segredo; trocar
      de provedor preserva remetente e URL base.

## 3. Persistência

- [ ] 3.1 Migração `V7`: adicionar `provider` e `brevo_api_key_ciphertext` a `smtp_settings`,
      fazer o backfill (`enabled = true` → `'SMTP'`, senão `'LOG'`) e remover `enabled`.
- [ ] 3.2 Mapear os campos novos em `SmtpSettingsEntity` e cifrar a chave com o `SecretCipher` já
      usado para a senha, com a mesma regra de "null preserva o valor gravado".
- [ ] 3.3 Teste de integração (Testcontainers) provando que a chave é gravada como ciphertext e
      volta decifrada, e que o backfill deriva o provedor de um registro pré-existente.

## 4. Adapter de saída — Brevo

- [ ] 4.1 Implementar `BrevoEmailSender` com `java.net.http.HttpClient` recebido por construtor,
      `POST` em `https://api.brevo.com/v3/smtp/email` com o header `api-key`, timeouts de 5s
      como o SMTP.
- [ ] 4.2 Mapear a falha para `EmailDeliveryException` com o detalhe do provedor — e garantir que
      nem a chave nem o header `api-key` apareçam na mensagem.
- [ ] 4.3 Fazer o `RoutingEmailSender` despachar pelo enum, com o caminho de falha sem fallback
      para log.
- [ ] 4.4 Testes com `HttpClient` mockado: sucesso, erro do provedor (4xx com corpo JSON), e a
      asserção de que a chave não vaza na exceção.

## 5. Fronteiras

- [ ] 5.1 Reescrever a regra `onlyAdoAdapterTalksHttp` em termos de uma lista nomeada de adapters
      de saída que falam com a rede, incluindo `adapter.outbound.email`, e renomeá-la para refletir
      a intenção (domínio/aplicação/entrada não abrem conexão).
- [ ] 5.2 Adicionar `java.net.http` ao `adapter-out-email` e confirmar que o ArchUnit passa.

## 6. Web — contrato e tela

- [ ] 6.1 Estender `ConfigDtos` com `provider` e `brevoApiKeySet` (nunca a chave), aceitando a
      chave apenas na escrita.
- [ ] 6.2 Ajustar a tela Admin → E-mail: seletor de provedor, campos do Brevo, e os campos de
      SMTP/Brevo mostrados conforme a escolha; o badge de status passa a refletir o provedor
      configurado.
- [ ] 6.3 Ajustar o texto do callout e o badge para que o modo log apareça como escolha
      deliberada, não como configuração faltando.
- [ ] 6.4 Documentar os campos novos em `docs/api/openapi.yaml`.

## 7. Verificação

- [ ] 7.1 Subir local (`docker compose up -d db` + `bootRun`), configurar o provedor Brevo com uma
      chave inválida e confirmar que o e-mail de teste falha reportando o erro do provedor — sem
      cair para log e sem vazar a chave.
- [ ] 7.2 Conferir na tela, com Chrome headless, que trocar de provedor mostra os campos certos e
      que o badge acompanha.
- [ ] 7.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
