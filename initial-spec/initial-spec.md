# PRD — Plataforma de Performance de Engenharia (Azure DevOps)

## Context

Não há hoje uma visão consolidada e comparável de performance dos times de engenharia. Os dados existem no Azure DevOps (Repos/PRs, Boards, Pipelines), mas espalhados e sem leitura por frequência ou por estrutura organizacional. A proposta é uma plataforma que **coleta métricas do Azure DevOps** e entrega **DORA, métricas de Fluxo e métricas de IA**, navegáveis por **frequência** (diária/semanal/mensal) e por **estrutura** (vertical → time → indivíduo), sempre com **evolução vs. o período anterior** e contexto de benchmark.

Inspiração em plataformas maduras (LinearB), com uma decisão de produto central: **medir para melhorar o sistema, não para vigiar pessoas**. Não existe leaderboard público nem comparação entre pessoas de times distintos. A **única** comparação individual permitida é a de um gestor entre os **próprios liderados**, como ferramenta de coaching, nunca exposta a pares ou a execs (ver RBAC).

Este documento é um **PRD (spec de produto)** para o MVP. Arquitetura técnica detalhada (stack, schema, pipeline de ingestão) fica para um documento seguinte.

## Objetivos

- Dar a gestores e execs uma leitura confiável de **saúde de entrega** por estrutura e por período.
- Cobrir **DORA completo**, **Fluxo** (código + work item) e **IA** (adoção e impacto).
- Tornar trivial **trocar frequência e estrutura** e ver **% de evolução/piora vs. baseline**.
- Posicionar métricas contra **benchmark DORA** e permitir **comparativo entre times/verticais**.

## Não-objetivos (fora do escopo do MVP)

- Leaderboard público de pessoas, ranking cross-org, ou comparação entre pessoas de times diferentes. (A comparação relativa entre os liderados de **um mesmo time**, visível só ao gestor daquele time, é permitida — ver Personas & RBAC.)
- Metas customizadas por time e % de atingimento (removido do MVP; ver backlog).
- Alertas/notificações proativas (push Teams/Slack/email) — só dashboard no MVP.
- Integração com ferramentas externas de incidente (PagerDuty/OpsGenie).
- APIs de telemetria de assistentes de IA (ex: Copilot Metrics API) — IA via convenção de commit.
- Múltiplas organizações ADO — apenas 1 org no MVP.
- Near-real-time — sincronização em batch diário.

## Personas & RBAC

| Persona | Vê | Não vê |
|---|---|---|
| **Exec / Head** | Verticais e times agregados, DORA de alto nível, comparativos entre estruturas | Indivíduos |
| **Eng Manager / Tech Lead** | Seu(s) time(s) em detalhe + **individual dos liderados** + **comparação relativa entre os próprios liderados** (coaching) | Individual de times que não lidera |
| **Individual Contributor** | Os **próprios** dados + agregado do seu time | Colegas individualmente |
| **Admin de plataforma** | Config: mapa org, integração ADO, convenção de IA | (não precisa ver métricas) |

**Regra transversal:** dado individual só é exposto ao próprio dono e ao seu gestor direto. A comparação entre pessoas existe **apenas dentro de um time e apenas para o gestor daquele time** (rankings Top-N e heatmap time→pessoas). Nunca há leaderboard público, comparação cross-time de pessoas, nem exposição de indivíduo a pares ou execs.

## Modelo de estrutura organizacional

- Cadastro **próprio** na plataforma (desacoplado do ADO), gerenciado pelo Admin: **Vertical → Time → Pessoa**.
- Cada Pessoa é vinculada às suas identidades no ADO (email/autor de commit, usuário de PR/work item) para atribuição correta das métricas.
- Um Time pode agregar dados de mais de um Project/Repo do ADO; uma Vertical agrega Times.
- Atribuição de métrica sobe na hierarquia: métricas de pessoa somam no time, times somam na vertical, verticais somam na visão geral.

## Catálogo de métricas

### DORA (4 completas)
| Métrica | Definição adotada | Fonte |
|---|---|---|
| **Deployment Frequency** | Nº de deploys para produção por período | Pipelines/Releases (stage de prod) |
| **Lead Time for Changes** | Do **1º commit** até o deploy em produção | Repos + Pipelines |
| **Change Failure Rate** | % de deploys que falharam ou sofreram rollback | Pipelines (release failed / rollback) |
| **MTTR** | Tempo entre a falha do deploy e o deploy de recuperação | Pipelines |

### Fluxo (código + work item)
- **Cycle Time de código (4 fases):** Coding time → PR pickup time → Review time → Deploy time.
- **Throughput + WIP:** itens concluídos por período e trabalho simultâneo em progresso.
- **PR size + review depth:** tamanho médio de PR, nº de revisores, comentários, % de PRs sem review.
- **Flow efficiency + cycle time de work item:** tempo ativo vs. esperando; lead/cycle time de story/bug (Boards).

### IA
- **% de commits com assistência de IA** (métrica-âncora do MVP).
- **Impacto da IA no fluxo:** comparação de cycle time / throughput de PRs *com IA* vs. *sem IA* ("a IA está ajudando?").
- **Detecção (MVP):** convenção no commit — trailer padrão (ex: `Co-authored-by: Copilot/Claude …` ou tag `[ai]`). Documentar a convenção como pré-requisito de adoção do time. Sem integração externa.

### Indivíduo (contribuição — só no painel individual)
Métricas com foco em **coaching e contribuição**, exibidas apenas no painel de pessoa (nunca agregadas em ranking cross-time). Derivam de Repos/PRs:
- **Mapa de contribuição de commits:** calendário estilo GitHub dos últimos 12 meses (intensidade por dia).
- **Taxa de assertividade de PRs:** % de PRs aprovados **sem pedir ajustes** (aprovação de 1ª passada).
- **Entrega (evolução):** throughput, tempo por ciclo e % de commits com IA da pessoa, com série temporal.
- **Contribuição em code review:** comentários feitos, **aprovações dadas**, **rejeições dadas**, e review **dados vs. recebidos**.
- **Distribuição de trabalho por tipo de tarefa:** % de esforço **e horas** por tipo (feature/bug/débito/etc.).

## Dimensões & comparação

- **Frequência:** Diária / Semanal / Mensal (seletor no topo, disponível em todos os níveis).
- **Estrutura:** Visão geral (todas) / Vertical / Time / Indivíduo (via navegação lateral, respeitando RBAC).
- **Visão (por estrutura):** cada estrutura tem 3 visões selecionáveis no topo — **Dashboard** (painel de cards + análises), **Tendências** (evolução temporal) e **Comparativo** (heatmap cross-estrutura). No nível **Indivíduo**, existe **apenas** o dashboard individual (sem Tendências/Comparativo).
- **Evolução:** cada métrica mostra **% de melhora/piora vs. o período imediatamente anterior** (diário=ontem, semanal=semana passada, mensal=mês passado).
- **Polaridade correta (regra de produto):** verde/vermelho refletem *bom/ruim*, não *subiu/desceu*. Ex: cycle time caindo = verde; deployment frequency caindo = vermelho. Cada métrica declara sua direção desejada.
- **Benchmark:** classificação **DORA (Elite/High/Medium/Low)** por métrica + **comparativo entre times/verticais**. Comparação de pessoas só dentro de um time e só para o gestor daquele time (ver RBAC).

## UX / Layout

- **Navegação lateral (esquerda):** árvore da estrutura organizacional (Vertical → Time → Pessoa), filtrada por RBAC, mais o acesso a **Admin**. Clicar troca o contexto do painel. (Sem "seções" fixas no menu — só estrutura + Admin.)
- **Topo:** seletor de **visão** (Dashboard / Tendências / Comparativo) + seletor de **frequência** (Diário / Semanal / Mensal) + breadcrumb da estrutura.
- **Home (default):** **visão geral agregada de todas as estruturas**, no Dashboard.
- **Cards de métrica:** valor atual + % de evolução (seta segue o sinal, cor segue a polaridade) + mini-tendência + selo de benchmark DORA quando aplicável.

### Visão Dashboard — 3 sub-dashboards
Sub-abas **DORA / Fluxo / IA**. Cada uma abre com os **cards do grupo no topo** e, abaixo, **análises elaboradas**:
- **Hero de evolução** (área) da métrica-âncora do grupo.
- **Rankings Top-N** e cartões **"maiores / menores"** (ex.: maior CFR, melhor MTTR, maior gargalo de fase, maior/menor adoção de IA).
- Gráficos específicos: **classificação DORA por time** (tiers), **scatter throughput × cycle time**, **cycle time por 4 fases**, **donut com IA vs. sem IA**, série **PRs com IA vs. sem IA**.
- Os rankings respeitam a estrutura: Visão geral/Vertical ranqueia **times**; Time ranqueia **pessoas** (visível só ao gestor).

### Visão Comparativo — heatmap
- **Heatmap cross-estrutura** (quadradinhos coloridos) de estruturas × métricas, sem lista de métricas solta.
- Node-aware: Visão geral → times (alterna verticais); Vertical → seus times; **Time → suas pessoas**; Pessoa → pares do próprio time (coaching).

### Painel individual (nível Pessoa)
Layout dedicado a **contribuição**, dividido em seções limpas:
- **Atividade de commits:** mapa de contribuição (12 meses) + gauge de **Taxa de assertividade de PRs**, lado a lado.
- **Entrega:** cards clicáveis (throughput / tempo por ciclo / % commits com IA) que trocam o gráfico de evolução abaixo.
- **Code reviews:** comentários, aprovações e rejeições dadas + comparação dados vs. recebidos.
- **Distribuição do trabalho:** % e **horas** por tipo de tarefa.
- **Drawer de atividade:** clicar no mapa abre à direita os últimos commits e PRs da pessoa, com **deep-link direto para o Azure DevOps**.

## Dados & Ingestão

- **Fontes ADO:** Repos + Pull Requests, Boards (Work Items), Pipelines (CI/CD).
- **Sincronização:** **batch diário** (madrugada).
- **Backfill inicial:** **6–12 meses** de histórico para baseline e tendências desde o primeiro uso.
- **Deploy = release em stage de produção no Pipelines**; falha/rollback alimentam CFR e MTTR.

## Autenticação

- **Login próprio (email/senha)**, independente do Azure AD.
- **1 organização ADO** no MVP.
- RBAC conforme a tabela de personas; vínculo pessoa↔identidades-ADO feito pelo Admin.

## Critérios de aceite (validação da spec)

1. Um EM consegue, em ≤2 cliques, ver DORA + Fluxo + IA do seu time no modo Semanal, com % de evolução vs. semana anterior.
2. Trocar o seletor de frequência (Diário/Semanal/Mensal) recalcula todos os cards e a evolução sem recarregar a estrutura.
3. Navegar Vertical → Time → Pessoa na lateral atualiza o painel à direita respeitando RBAC (IC não vê colega; Exec não vê indivíduo).
4. Cada métrica DORA exibe classificação Elite/High/Medium/Low.
5. Métrica de IA mostra % de commits com IA e o comparativo de fluxo com/sem IA para a estrutura selecionada.
6. Polaridade correta: uma piora real aparece em vermelho mesmo quando o número "subiu" (ex: cycle time).
7. Após backfill, tendências dos últimos ≥6 meses estão disponíveis em todos os níveis.
8. No Dashboard, as sub-abas DORA/Fluxo/IA mostram os cards do grupo no topo e ao menos um ranking Top-N + um cartão "maior/menor" por grupo.
9. No nível Time, o gestor vê ranking e heatmap das próprias pessoas; no nível Pessoa só há o painel individual (sem Tendências/Comparativo) e nenhum outro usuário compara aquela pessoa com colegas.
10. O painel individual exibe mapa de commits, taxa de assertividade de PRs, contribuição em code review e distribuição de trabalho por tipo (com horas), e o drawer de atividade linka commits/PRs direto ao Azure DevOps.

## Fases futuras (backlog registrado)

- Alertas de anomalia + digest e regras por meta (Teams/Slack/email).
- Metas customizadas por time e % de atingimento.
- Métricas de IA avançadas (adoção por pessoa, % de linhas, integração Copilot Metrics API).
- CFR/MTTR enriquecidos por work item de incidente e/ou ferramenta externa.
- Near-real-time via webhooks; múltiplas orgs ADO; SSO Entra ID.

## Perguntas em aberto

- Convenção exata de trailer de IA a padronizar (Copilot, Claude, Cursor…?).
- Como identificar o "stage de produção" nos Pipelines de forma genérica entre projetos (nome padrão vs. config por Time).
- Regra de deduplicação de identidade quando a mesma pessoa usa múltiplos emails de commit.

## Verificação (próximos passos após aprovação)

Como este entregável é um PRD (não código), a "verificação" é a validação do documento:
1. Revisar o catálogo de métricas e definições com 1 EM e 1 Exec para confirmar que respondem às perguntas de negócio deles.
2. Validar viabilidade de captura de cada métrica contra a API do Azure DevOps (amostra real de 1 projeto: Deploy Freq, Lead Time, CFR/MTTR via releases).
3. Confirmar com o time a convenção de commit de IA antes de depender dela.
4. Só então evoluir para o documento de **arquitetura técnica** (stack, modelo de dados, pipeline de ingestão, agregações).
