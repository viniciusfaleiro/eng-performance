# CLAUDE.md — eng-performance

Regras que **todo agente** deve seguir neste repositório. Em conflito, a ordem de
precedência é: pedido explícito do usuário → este arquivo → README.md.

## O que é

Plataforma que mede performance de times de engenharia a partir do **Azure DevOps**
(métricas **DORA**, **Fluxo** e **IA**). O PRD do produto está em
[`docs/initial-spec.md`](docs/initial-spec.md) — é a fonte de verdade
do **o quê** medimos. O protótipo navegável de UX está em `prototype/`.

Decisão de produto que atravessa tudo: **medir para melhorar o sistema, não vigiar
pessoas** — sem ranking público e sem comparação de pessoas entre times; a visão
individual é coaching do gestor sobre os **próprios** liderados.

## Estado atual

O **walking skeleton está completo ponta a ponta** (S1–S9 do
[`openspec/roadmap.md`](openspec/roadmap.md)): estrutura & cadastro · contas, login
e RBAC · motor de métricas + shell de navegação · dashboards **DORA**, **Fluxo** e
**IA** · heatmap comparativo · painel individual · adapter real do **Azure DevOps**
(login device-code, sem PAT; backfill + watermark incremental).

Não faça over-modeling: só implemente o que a change atual pedir.

**O motor de métricas é o coração.** Eventos são gravados **crus** em `raw_event`
(commit, PR, deploy, work item, review com timestamps) e agregados **on-read** por
dimensão × frequência × estatística (sum/median/ratio/snapshot). Números de time são
recalculados sobre a população (**nunca média de médias**), a atribuição é
**as-of-event** (o período fica com o time de registro mesmo depois de a pessoa mudar)
e a cobertura acompanha eventos atribuídos vs. não atribuídos. O adapter do ADO só
troca a **fonte** desses eventos — a mesma tabela `raw_event`.

As métricas de **Fluxo** (cycle time, throughput, flow efficiency, flow lead time) são
ancoradas no **work item** do Azure Boards (histórico de estados); `pr_size`,
`pr_review_time` e as métricas de código seguem vindo do **PR**, como drill-down.

## Arquitetura (hexagonal — imposta por ArchUnit)

Dependências só podem apontar para dentro. Violou → o build quebra.

| Módulo | Papel | Pode depender de |
|---|---|---|
| `domain` | Regras de negócio puras. **Sem framework, sem Spring.** | — |
| `application` | Use cases + ports (in/out). **Sem Spring.** | `domain` |
| `adapter-in-web` | HTTP + a SPA servida (inbound). | `application`, `domain` |
| `adapter-out-persistence` | Impl dos ports de saída — **PostgreSQL (JPA + Flyway)**. | `application`, `domain` |
| `adapter-out-ado` | A fonte real (**Azure DevOps** + device-code do Entra) — **único** módulo que fala HTTP. | `application`, `domain` |
| `adapter-out-email` | E-mail transacional — SMTP, com fallback de log sem servidor configurado. | `application`, `domain` |
| `bootstrap` | App executável; composition root (liga ports→adapters). | todos |
| `architecture-tests` | Regras ArchUnit que guardam as fronteiras. | todos (test) |

Os módulos são agrupados em disco por camada (`core/`, `adapters/`, `app/`, `test/`),
mas os nomes lógicos seguem planos (`:domain`, `:adapter-in-web`, …) — ver
`settings.gradle.kts`.

Frontend = **design system próprio do protótipo** (`prototype/index.html` é a spec
visual), servido como SPA **self-contained** pelo `adapter-in-web`
(`static/index.html` + `static/css/app.css`): CSS + JS vanilla, **sem build de
frontend e sem CDN**. O `@material/web`/MD3 foi **descontinuado** — reusamos o
CSS/markup do protótipo.

## Banco de dados (persistência durável — nada em memória)

Todo slice grava em **PostgreSQL**; schema por **Flyway** (`db/migration`), mapeado
por **JPA** dentro do `adapter-out-persistence` (domínio/aplicação nunca veem JPA).
Fixtures são semeadas no banco por um seeder **idempotente**.

- **Rodar o app:** `docker compose up -d db` (Postgres local, dados persistem em
  volume) e então `./gradlew :bootstrap:bootRun`. Config via env
  `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` (defaults batem com o compose).
- **Testes de integração:** rodam contra Postgres real via **Testcontainers**; o
  Gradle não precisa de banco pré-existente para eles. (Neste host, o daemon Docker
  é muito novo — piso de API 1.40 — e exigiu baixar o piso para 1.24 via
  `scripts/fix-docker-min-api.sh`; sem isso o Testcontainers não conecta.)

## Gates de qualidade

**Todos em `./gradlew build`:** Spotless (google-java-format) · Checkstyle (`maxWarnings=0`) ·
SpotBugs+FindSecBugs · JaCoCo (**pisos de 70% de linha e 60% de branch** em
`domain`+`application`) · ArchUnit (fronteiras + "só o `adapter-out-ado` fala HTTP").
A UI é vanilla JS/CSS inline (sem build de frontend).

Fora de escopo por decisão do produto: **scanning de segurança** (vulnerabilidades de
dependências, secrets) — não faz parte deste harness. SpotBugs+FindSecBugs cobre análise
estática de segurança do código; scanners de supply-chain não são usados.

## ⛔ Regra de gate — SEMPRE rode o harness antes de subir

Antes de **qualquer** `git commit`/`git push`, e antes de declarar uma tarefa concluída:

```bash
./gradlew spotlessApply   # formata (formatação é a fonte de verdade)
./gradlew build           # todos os gates; precisa dar BUILD SUCCESSFUL
```

- **Nunca** commite com o build vermelho. Se um gate falhar, corrija a causa —
  não desabilite regra, não suprima warning, não baixe o piso de cobertura sem
  o usuário pedir explicitamente.
- Os git hooks (`hooks/`, ativados via `core.hooksPath=hooks`) reforçam isso
  (pre-commit = format+checkstyle, pre-push = build completo), mas o agente **não**
  deve depender do hook: rode o build proativamente.
- Commit inicial já feito; mensagens de commit = uma linha imperativa curta.

## Fluxo de trabalho — Spec-Driven Development (openspec)

Mudanças de comportamento passam por **openspec** (`openspec/`, schema `spec-driven`).
Não edite specs a mão em `openspec/specs/` — use o fluxo:

1. `/opsx:propose "<ideia>"` — cria a change com `proposal.md`, `design.md`, `tasks.md`.
2. `/opsx:apply` — implementa seguindo `tasks.md` (a última tarefa é rodar o build).
3. `/opsx:archive` — arquiva a change e promove os deltas para `openspec/specs/`.

`openspec validate` valida changes/specs. O contexto do projeto e regras de artefato
estão em `openspec/config.yaml`. O **PRD** (`docs/initial-spec.md`) é o documento de produto
de alto nível; as **specs do openspec** são a verdade viva por capacidade.

## Comandos comuns

```bash
docker compose up -d db        # Postgres local (necessário para rodar o app)
./gradlew build                # compila + todos os gates (Testcontainers p/ o teste de DB)
./gradlew spotlessApply        # auto-formata
./gradlew :bootstrap:bootRun   # roda o app em http://localhost:8080
openspec list                  # changes ativas
```
