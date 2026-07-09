# Roadmap de slices — eng-performance

Sequência de slices verticais para construir a plataforma sobre o harness. Cada
slice vira uma change do openspec (`/opsx:propose → apply → archive`), exercita
**todas as camadas hexagonais** e **entrega também sua página MD3**, fechando com
`./gradlew build` verde.

**Decisões de fatiamento (combinadas):**
- **Fixtures primeiro:** adapters in-memory com eventos-semente atrás das portas; o
  **adapter real do ADO é o último slice** (S9), sem retrabalho.
- **Por grupo de métrica:** DORA / Fluxo / IA entram como slices inteiros (não uma
  métrica por vez), cada um com seu dashboard composto.
- **MD3 por slice:** cada slice server-renderiza sua tela (o protótipo em
  `prototype/` é a spec visual de referência).
- Contratos em `api/openapi.yaml`; modelo de agregação/cadastro no PRD
  (`initial-spec/initial-spec.md`).

## Fase A — Fundação (compartilhada por todos os grupos)

### S1 · Estrutura & cadastro
- **Domínio:** Vertical, Time, Pessoa, **TeamMembership (vigência / as-of-event)**,
  Repo, Identidade. Invariantes: 1 repo → 1 time; hierarquia fixa 3 níveis.
- **App:** use-cases CRUD + `StructureRepositoryPort`.
- **Adapter-out:** in-memory.
- **Web/MD3:** `/structure/tree`, `/admin/{verticals,teams,people}`, `team-change`,
  `/admin/ado/committers`, `/admin/coverage` (stub); telas Admin **Estrutura /
  Identidades / Repositórios**.
- **Aceite:** montar Vertical→Time→Pessoa, mover pessoa preservando histórico,
  vincular identidade e repo; build verde.

### S2 · Contas, login & RBAC
- **Domínio:** UserAccount (email/senha/perfil/status ↔ Pessoa); política de escopo
  por persona.
- **Web/MD3:** `/auth/login|logout|me|password`, `/admin/users`; **tela de login** +
  Admin **Usuários**. Enforcement de RBAC (403 por nó; comparação de pessoas
  coaching-only) — consumido pelos slices de métrica.
- **Aceite:** criar conta, logar, escopo do perfil respeitado.

### S3 · Motor de métricas + shell de navegação
- **Domínio:** eventos crus (Commit, PR, Deploy, WorkItem c/ timestamps); catálogo
  de métricas com `attributionScope` (person|repo) e `aggregation`
  (sum|median|ratio|snapshot).
- **App:** `EventStorePort` (ingestão) + **motor de agregação on-read** — dois
  caminhos de atribuição, roll-up (soma / mediana sobre população / taxa ponderada /
  snapshot fim de período), bucketing Diário/Semanal/Mensal, evolução vs. período +
  `direction`/`sentiment`, balde "Não atribuído" + cobertura.
- **Adapter-out:** fixture com eventos-semente.
- **Web/MD3:** `/metrics/catalog`, `/metrics/cards`, `/metrics/{key}/series`
  genéricos; **shell** (topbar frequência/visão) + visão **Tendências**.
- **Aceite:** engine validado por testes com fixtures cobrindo cada tipo de
  agregação e ambos os caminhos de atribuição.

## Fase B — Grupos de métrica (métricas + dashboard composto + MD3)

### S4 · Grupo DORA
- Deployment Frequency (count, repo), Lead Time (median, repo), CFR (ratio, repo),
  MTTR (median, repo) + **benchmark tiers** + **rankings**.
- **Web/MD3:** `/dashboards/dora` + tela (cards, hero, ranking Top-N, tabela de
  tiers, stats maiores/menores).

### S5 · Grupo Fluxo
- Cycle time (4 fases), Throughput (person), WIP (snapshot fim de período), PR
  review time, PR size, Flow efficiency.
- **Web/MD3:** `/dashboards/flow` + tela (scatter throughput×cycle, fases).

### S6 · Grupo IA
- Convenção de commit (trailer/tag), % commits com IA, adoção, com/sem IA.
- **Web/MD3:** `/dashboards/ai` + tela (donut, série com/sem IA); Admin **Convenção
  de IA**.

## Fase C — Visões transversais

### S7 · Comparativo (heatmap)
- `/comparison/heatmap` node-aware (times/verticais/pessoas) + tela MD3.

### S8 · Painel individual
- Mapa de contribuição, taxa de assertividade de PRs, entrega (evolução),
  code review (comentários/aprovações/rejeições, dados vs. recebidos), distribuição
  por tipo (% + horas), drawer de atividade com deep-link ADO.

## Fase D — Integração real

### S9 · Adapter ADO real
- Implementa `EventStorePort` contra Azure DevOps (Repos/PRs, Boards, Pipelines);
  sync batch diário + backfill 6–12 meses; stage de produção global + override por
  time. Troca os fixtures atrás da porta — sem tocar domínio/app.

---

**Walking skeleton:** S1→S4 já entregam cadastro + login + eventos + o grupo DORA
ponta-a-ponta com tela. De S5 em diante, cada slice é incremento independente.
