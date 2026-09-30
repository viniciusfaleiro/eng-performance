# Cancelada em 2026-09-30

Cancelada pelo usuário sem nenhuma tarefa implementada (0/24). **Nada desta change chegou ao
código**: não há provedor Brevo, a tela Admin → E-mail não mudou, a regra de ArchUnit "só o
`adapter-out-ado` fala HTTP" continua como era, e nenhum delta foi promovido para
`openspec/specs/`. O envio transacional segue como está — SMTP quando configurado, modo log quando
não.

## O que continua valendo

O diagnóstico do bloqueio é a parte que sobrevive, e é a razão de esta pasta ficar arquivada em vez
de ser apagada: **a rede de homologação bloqueia SMTP de saída silenciosamente** — a conexão TCP
para `smtp.gmail.com:587` é estabelecida, mas o servidor nunca envia o banner `220` e o envio morre
no timeout. HTTPS na 443 funciona do mesmo container. Quem for reabrir o assunto começa daqui, em
vez de redescobrir isso por timeout.

Consequência prática que permanece: o **reset de senha por e-mail só existe em modo log** em
homologação, então esse fluxo não foi validado com usuário real.

## Se for reabrir

Além de reavaliar o provedor, duas coisas precisam ser tratadas antes de qualquer código:

- Uma liberação de firewall para SMTP resolveria o problema sem provedor novo, e não foi tentada
  até o fim. É o caminho mais curto se a rede for negociável.
- As chaves de API do Brevo que circularam durante a análise devem ser consideradas **comprometidas**
  e revogadas. Nenhuma delas foi para o repositório — verificado no cancelamento — mas elas
  passaram por canal de conversa, o que basta para não voltarem a ser usadas.
