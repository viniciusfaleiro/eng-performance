# CLAUDE.md — eng-performance

Regras que **todo agente** deve seguir neste repositório. Em conflito, a ordem de
precedência é: pedido explícito do usuário → este arquivo → README.md.

## O que é

Plataforma que mede performance de times de engenharia a partir do **Azure DevOps**
(métricas **DORA**, **Fluxo** e **IA**). O PRD do produto está em
[`initial-spec/initial-spec.md`](initial-spec/initial-spec.md) — é a fonte de verdade
do **o quê** medimos. O protótipo navegável de UX está em `prototype/`.

Hoje o repo tem o **harness de engenharia completo** + uma fatia **"echo" trivial**
que exercita todas as camadas. O domínio real é modelado **depois**, sobre este harness
— não faça over-modeling: só implemente o que a change atual pedir.

## Arquitetura (hexagonal — imposta por ArchUnit)

Dependências só podem apontar para dentro. Violou → o build quebra.

| Módulo | Papel | Pode depender de |
|---|---|---|
| `domain` | Regras de negócio puras. **Sem framework, sem Spring.** | — |
| `application` | Use cases + ports (in/out). **Sem Spring.** | `domain` |
| `adapter-in-web` | HTTP + Thymeleaf UI (inbound). | `application`, `domain` |
| `adapter-out-persistence` | Impl dos ports de saída — **PostgreSQL (JPA + Flyway)**. | `application`, `domain` |
| `bootstrap` | App executável; composition root (liga ports→adapters). | todos |
| `architecture-tests` | Regras ArchUnit que guardam as fronteiras. | todos (test) |

Frontend = **design system próprio do protótipo** (`prototype/` é a spec visual),
server-renderizado com Thymeleaf + JS vanilla, **self-contained (sem CDN)**. O
`@material/web`/MD3 foi **descontinuado** — reusamos o CSS/markup do protótipo.

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

**Backend (todos em `./gradlew build`):** Spotless (google-java-format) · Checkstyle ·
SpotBugs+FindSecBugs · JaCoCo (**pisos de 70% de linha e 60% de branch** em
`domain`+`application`) · ArchUnit.

**Frontend (`frontend/`, npm):** ESLint + Prettier + build do bundle de assets, via
`npm run verify`. Aplicados pelos git hooks (pre-commit = lint+format quando há
mudança em `frontend/`; pre-push = `verify` completo). Requer `npm --prefix frontend ci` uma vez.

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
estão em `openspec/config.yaml`. O **PRD** (`initial-spec/`) é o documento de produto
de alto nível; as **specs do openspec** são a verdade viva por capacidade.

## Comandos comuns

```bash
docker compose up -d db        # Postgres local (necessário para rodar o app)
./gradlew build                # compila + todos os gates (Testcontainers p/ o teste de DB)
./gradlew spotlessApply        # auto-formata
./gradlew :bootstrap:bootRun   # roda o app em http://localhost:8080
openspec list                  # changes ativas
```
