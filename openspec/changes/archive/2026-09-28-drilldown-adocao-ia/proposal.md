## Why

Clicar no card "Adoção de IA (devs)" hoje abre uma lista de **eventos de commit** — a mesma lista
genérica que todo card mostra. Mas a métrica não conta commits: ela conta **pessoas** que usaram IA
ao menos uma vez, sobre as pessoas que commitaram. A lista de itens responde uma pergunta que ninguém
fez, e não responde a que importa: quem está usando e quem não está.

Essa é a pergunta que transforma o número em ação. "58% de adoção" não diz o que fazer; "estas
pessoas ainda não usaram" diz — é onde o gestor abre uma conversa, oferece treinamento ou descobre
um impedimento (licença faltando, tipo de trabalho que não se beneficia).

## What Changes

- O drawer do card de adoção de IA passa a mostrar **duas listas de pessoas**: quem mais usou IA no
  período e quem não usou (ou usou menos).
- As listas cobrem a população do **nó selecionado**: na visão geral, as pessoas do escopo de quem
  olha; numa vertical, as pessoas daquela vertical; num time, as do time.
- Uma pessoa só aparece para quem já pode vê-la individualmente — a mesma regra que o ranking de
  adoção e o heatmap comparativo aplicam.
- Cada pessoa mostra com o que contribuiu: commits com IA e commits totais no período, não apenas
  uma posição na lista.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `ai-dashboard`: a adoção de IA passa a ser detalhável por pessoa, nos dois extremos.
- `metric-drilldown`: o detalhamento deixa de ser sempre uma lista de eventos — uma métrica cuja
  unidade é a pessoa detalha por pessoa.

## Non-goals

- **Não** expor pessoas fora do escopo individual de quem olha. A lista não é pública.
- **Não** mudar o cálculo da adoção.
- **Não** criar alerta, meta ou cobrança automática sobre quem não usa IA. A plataforma mostra; a
  conversa é de quem lidera.
- **Não** estender este tipo de detalhamento às outras métricas neste change.

## Impact

- **`application`**: um detalhamento por pessoa para a adoção, sobre os mesmos eventos de commit.
- **`adapter-in-web`**: endpoint e filtro de escopo individual; o drawer passa a escolher entre a
  lista de itens e a lista de pessoas conforme a métrica.
- **Risco de produto**: uma lista de "quem não usa IA" é, por construção, uma lista de pessoas
  associadas a algo que a organização quer mudar. Ver `design.md` — a mitigação é o escopo e o
  enquadramento, e vale o time saber que ela existe antes de virar rotina.
