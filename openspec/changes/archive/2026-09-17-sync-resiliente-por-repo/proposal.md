## Why

Um repositório inacessível derruba a sincronização inteira. Basta um repo renomeado, removido ou
fora das permissões do usuário — como `asa-pay-logistics-delivery-manager`, que respondeu
`TF401019` — e nenhum dado entra, nem dos repositórios que estavam perfeitamente acessíveis.

O efeito prático é que a plataforma para de medir por causa de cadastro desatualizado. E o custo
cresce com a adoção: quanto mais repositórios registrados, maior a chance de algum estar quebrado
num dado dia, e mais frequente o "tudo ou nada".

## What Changes

- Uma falha ao coletar um repositório **não interrompe** a sincronização: o sistema registra o erro
  e segue para o próximo.
- O mesmo vale para a coleta por projeto (pipelines e work items): um projeto inacessível não
  impede os outros.
- Ao final, a sincronização apresenta **a lista do que falhou**, com o identificador do repositório
  ou projeto e o motivo, junto do resumo de eventos coletados.
- Uma sincronização com falhas parciais **não avança o watermark**. Os dados dos repos que
  funcionaram são gravados, mas a janela é reprocessada na próxima execução, para que a lacuna do
  repo quebrado não se torne permanente.
- Só uma falha que impeça qualquer coleta — problema de login, nenhum repositório cadastrado —
  continua abortando a sincronização.

## Capabilities

### New Capabilities

Nenhuma. É a mesma ingestão, com falha isolada por fonte em vez de global.

### Modified Capabilities

- `ado-integration`: a sincronização passa a concluir com falhas parciais, a reportá-las, e a tratar
  o watermark de forma diferente quando houve falha.

## Non-goals

- **Não** tentar novamente automaticamente dentro da mesma execução. Um 404 de repositório
  inexistente não melhora com retentativa; o que resolve é corrigir o cadastro.
- **Não** remover ou desabilitar automaticamente o repositório que falhou. A plataforma reporta;
  quem decide é quem administra — um 404 pode ser permissão temporária, não repo morto.
- **Não** mudar o que é coletado nem como é mapeado.
- **Não** criar alerta, e-mail ou notificação sobre falhas. Elas aparecem no resultado da
  sincronização e no log.

## Impact

- **`application`**: o port de ingestão passa a devolver, além dos eventos, o que falhou; o serviço
  de sincronização decide o watermark com base nisso e expõe as falhas no status.
- **`adapter-out-ado`**: o laço por repositório e o laço por projeto capturam a falha, registram e
  continuam.
- **`adapter-in-web`**: o status de sincronização carrega a lista de falhas, e a tela as apresenta.
- **Operação**: um repositório que falhe de forma persistente mantém o watermark parado, e a
  janela de 6 meses é reprocessada a cada sincronização até o cadastro ser corrigido. É deliberado —
  o alternativo seria uma lacuna silenciosa nos dados — mas precisa estar visível para quem opera.
