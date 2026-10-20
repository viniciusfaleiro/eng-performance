## Why

O título da requirement corrigida ontem ainda diz **"without changing work-in-progress"**, enquanto
o corpo dela, já corrigido, diz que o tempo impedido sai do trabalho em progresso. Quem lê a spec lê
o título primeiro — e é exatamente o tipo de verdade vencida que criou a contradição que acabamos de
fechar.

## What Changes

- O título da requirement passa a dizer o que ela diz: o tempo impedido sai das duas leituras.
- Nenhuma mudança de texto no corpo, de cenário, de código ou de número.

## Capabilities

### Modified Capabilities
- `impediment-signal`: o título da requirement sobre tempo impedido deixa de contradizer o corpo.

## Non-goals

- **Não mexer em comportamento**: esta change não toca em código.
- **Não reescrever o corpo nem os cenários**, que já estão corretos.

## Impact

- Apenas `openspec/specs/impediment-signal/spec.md`.
