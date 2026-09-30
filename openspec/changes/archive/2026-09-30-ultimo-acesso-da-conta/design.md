## Context

`AuthService.login` valida o e-mail, o status e a senha, e emite o token. Não escreve nada — a
autenticação é hoje uma operação de leitura pura sobre `user_account`, e a tabela não tem nenhuma
coluna temporal.

O painel individual é *coaching-only*: só quem pode ver a pessoa individualmente o alcança. A lista
de Admin → Usuários já exige `canConfigure`.

Uma diferença que atravessa o desenho: uma **conta** é o que faz login; uma **pessoa** é o que a
plataforma mede. Nem toda pessoa tem conta, e a ligação é o `personId` opcional da conta.

## Goals / Non-Goals

**Goals:**

- Saber, por conta, quando foi o último acesso — e distinguir "nunca acessou" de "faz tempo".
- Responder "estão usando?" numa tela só, sem abrir o painel de cada pessoa.

**Non-Goals:**

- Histórico de sessões, telemetria de uso, ranking por frequência, desativação automática.

## Decisions

### 1. Um instante por conta, sobrescrito — não um histórico

Guardar só o último acesso responde a pergunta feita ("estão acessando?") e não abre a porta para as
que não foram feitas. Um log de sessões permitiria reconstruir rotina de trabalho — que horas a
pessoa entra, com que frequência, em que dias — e isso é vigilância, não adoção.

A escolha também é o caminho barato: uma coluna, uma escrita por login, nada para expirar ou podar.

*Alternativa considerada:* tabela de sessões com histórico. Rejeitada pelo que ela permitiria, não
pelo custo.

### 2. Nulo significa "nunca acessou", e a tela diz isso

A coluna é anulável e nasce nula para toda conta existente. Isso é informação real e é justamente a
mais útil numa implantação: a conta foi criada e a pessoa nunca entrou.

A tela **não** pode mostrar isso como "—" ou como uma data inventada. É a mesma regra que já
aplicamos aos painéis: ausência se mostra como ausência, e aqui a ausência tem significado próprio.

### 3. A escrita não pode derrubar o login

Atualizar o acesso é efeito colateral do login, não parte dele. Se a escrita falhar, o usuário entra
assim mesmo e o campo fica desatualizado — perder o login de alguém para registrar uma estatística
de adoção seria inverter a prioridade.

### 4. O painel individual mostra acesso separado da entrega

O painel mede o trabalho de engenharia da pessoa. O acesso à plataforma é outra natureza: mede
adesão a uma ferramenta, não produção. Fica fora da fileira de cards de entrega e com rótulo que
diz o que é, para ninguém ler "não acessa há 20 dias" como parte do desempenho.

A pessoa pode não ter conta — aí não há o que mostrar, e a tela omite em vez de afirmar nada.

### 5. A pergunta de adoção se responde no Admin

Descobrir quem não usa abrindo o painel de cada pessoa seria trabalho manual proporcional ao time. A
lista de contas já existe, já é restrita a quem administra, e é onde a pergunta cabe: uma coluna a
mais responde de uma vez.

## Risks / Trade-offs

- **"Último acesso" vira métrica de cobrança** → é o risco real, e nenhuma implementação o elimina.
  Mitigações: sem ranking, sem comparação, sem gráfico; rótulo que nomeia a coisa como acesso à
  plataforma; e a colocação fora dos cards de entrega no painel individual. Vale dizer ao time o
  que é, antes de alguém descobrir sozinho e presumir o pior.

- **Conta compartilhada ou login por serviço distorce o número** → uma conta de serviço apareceria
  como "acessa sempre". Não há tratamento; se aparecer, é sinal de que contas de serviço deveriam
  ser marcadas como tal, o que é outro assunto.

- **O dado só existe daqui pra frente** → não há como saber quem acessava antes do deploy. Todas as
  contas começam em "nunca acessou", e isso vai parecer alarmante no primeiro dia. Precisa ser
  comunicado junto.

## Migration Plan

Migração Flyway adicionando a coluna anulável. Sem backfill — não existe fonte para ele, e inventar
um valor inicial seria pior que o nulo honesto.

## Open Questions

- Vale destacar visualmente contas sem acesso há muito tempo (ex.: mais de 30 dias)? Ajuda a
  encontrar o caso, mas aproxima a tela de um placar — melhor decidir vendo a lista real.
