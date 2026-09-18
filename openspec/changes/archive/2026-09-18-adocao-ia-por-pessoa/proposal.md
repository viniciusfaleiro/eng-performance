## Why

No nível de time, o painel "Adoção de IA" lista as pessoas com **0%** para todo mundo. Não é o dado
— é o frontend inventando: a API não devolve ranking nesse nível, e a tela cai num gerador
sintético herdado do protótipo, que hoje só produz zeros. O resultado é indistinguível de "ninguém
no time usa IA", que é uma afirmação forte e falsa.

São dois problemas sobrepostos:

- **A informação não existe nesse nível.** O ranking foi deliberadamente limitado a verticais e
  times, para não comparar pessoas publicamente. Mas o heatmap comparativo **já** lista pessoas
  dentro do time, sob RBAC — as duas telas aplicam o mesmo princípio de formas diferentes. E a
  decisão de produto permite exatamente esse caso: a visão individual é coaching do gestor sobre os
  **próprios** liderados (`docs/initial-spec.md`).
- **A ausência de dado vira número.** Qualquer painel sem resposta da API mostra zero em vez de
  dizer que não tem dado. Isso já confundiu antes: os cards de Commits/PRs zerados e as colunas
  fantasma do heatmap tinham a mesma origem.

## What Changes

- O ranking de adoção de IA passa a existir **no nível de time**, listando as pessoas do time, com o
  mesmo controle de acesso que o heatmap já aplica: quem não pode ver uma pessoa não a recebe.
- Continua **não existindo** ranking no nível de pessoa, e pessoas de times diferentes seguem sem
  comparação entre si.
- O gerador sintético deixa de ser o destino quando falta dado. Um painel sem resposta da API passa
  a dizer **"sem dados"**, e um valor genuinamente zero continua aparecendo como zero.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `ai-dashboard`: o ranking de adoção passa a cobrir as pessoas de um time, respeitando o escopo.
- `metrics-navigation`: ausência de dado passa a ser apresentada como tal, não como zero.

## Non-goals

- **Não** comparar pessoas de times diferentes, nem publicar ranking de pessoas fora do escopo de
  quem olha. A regra do produto não muda; muda onde ela é aplicada de forma consistente.
- **Não** remover as linhas de pessoa do heatmap — a inconsistência é resolvida igualando o painel
  ao heatmap, não o contrário.
- **Não** mudar cálculo de nenhuma métrica.

## Impact

- **`application`**: `AiDashboardService` passa a resolver as pessoas de um time como filhos.
- **`adapter-in-web`**: o filtro de escopo do ranking passa a distinguir pessoa de nó de estrutura,
  como o heatmap faz; a SPA deixa de cair no gerador e ganha o estado "sem dados".
- **Risco**: tornar visível a ausência de dado vai expor painéis que hoje parecem preenchidos. É o
  objetivo, mas muda a aparência de telas que ninguém reclamou.
