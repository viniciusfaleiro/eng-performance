## 1. Domínio e persistência

- [x] 1.1 `UserAccount` passa a carregar o instante do último acesso (anulável = nunca acessou), com
      um método para produzir a conta com o acesso registrado.
- [x] 1.2 Migração Flyway adicionando a coluna anulável a `user_account`, sem backfill.
- [x] 1.3 Mapear a coluna na entidade JPA e no repositório.
- [x] 1.4 Teste de integração: o valor vai e volta, e nasce nulo para conta existente.

## 2. Login

- [x] 2.1 `AuthService.login` registra o acesso após autenticar, usando o relógio injetado.
- [x] 2.2 Falha ao registrar não impede o login (capturada e logada).
- [x] 2.3 Testes: login bem-sucedido atualiza; login recusado (senha errada, conta desabilitada) não
      atualiza; falha na escrita não derruba o login.

## 3. Admin → Usuários

- [x] 3.1 O DTO de conta passa a expor o último acesso.
- [x] 3.2 A tabela de usuários ganha a coluna, com "nunca acessou" explícito para o nulo.
- [x] 3.3 Documentar o campo em `docs/api/openapi.yaml`.

## 4. Painel individual

- [x] 4.1 O painel resolve a conta da pessoa (pelo vínculo pessoa↔conta) e expõe o último acesso.
- [x] 4.2 Renderizar fora da fileira de cards de entrega, rotulado como acesso à plataforma; omitir
      quando a pessoa não tem conta.
- [x] 4.3 Teste: pessoa com acesso, pessoa que nunca acessou, pessoa sem conta.

## 5. Verificação

- [x] 5.1 Com Chrome headless: fazer login, conferir que o Admin passa a mostrar o acesso de agora e
      que outra conta segue como "nunca acessou".
- [x] 5.2 Conferir no painel individual que o acesso aparece separado dos cards de entrega.
- [x] 5.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
