## Why

Ninguém sabe se a plataforma está sendo usada. O sistema mede a engenharia do time, mas não mede a
própria adoção: não há registro de quem entrou, quando, ou quem nunca entrou desde que a conta foi
criada.

Isso importa porque a plataforma só produz efeito através de conversa. Um dashboard que ninguém abre
não melhora processo nenhum — e hoje o sinal de que isso está acontecendo simplesmente não existe.
Quem conduz a implantação descobre pelo silêncio, tarde.

## What Changes

- Toda autenticação bem-sucedida passa a registrar o **momento do acesso** na conta.
- **Admin → Usuários** passa a mostrar o último acesso de cada conta, incluindo quem **nunca
  acessou** — que é o caso que mais interessa numa implantação.
- O **painel individual** mostra o último acesso daquela pessoa, apresentado como informação de
  acesso à plataforma e **separado das métricas de entrega**.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `authentication`: o login passa a deixar rastro do acesso.
- `user-accounts`: a conta passa a carregar e expor o último acesso.
- `individual-dashboard`: o painel passa a informar o acesso da pessoa à plataforma.

## Non-goals

- **Não** registrar histórico de sessões, endereço IP, navegador, páginas visitadas ou tempo de uso.
  O que se guarda é um instante por conta, sobrescrito a cada login.
- **Não** comparar pessoas por frequência de acesso, nem ranquear. Isso não é métrica de
  desempenho, e tratá-la como tal é o uso errado.
- **Não** alterar login, sessão, expiração de token ou qualquer regra de autenticação.
- **Não** desabilitar conta automaticamente por inatividade.

## Impact

- **`domain`/`application`**: a conta ganha o instante do último acesso; o login o atualiza.
- **`adapter-out-persistence`**: uma coluna nova, anulável (migração Flyway) — nulo significa
  "nunca acessou", que é informação, não ausência de dado.
- **`adapter-in-web`**: o campo na listagem de contas e no painel individual.
- **Contas existentes**: ficam sem acesso registrado até o primeiro login depois do deploy. A tela
  deve dizer "nunca acessou" com honestidade, sem fingir que a informação é anterior a existir.
