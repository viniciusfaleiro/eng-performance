## Context

Primeiro slice real sobre o harness hexagonal (Java 21 / Spring Boot 3.4, Gradle
multi-módulo). O echo slice já prova as camadas; aqui trocamos o domínio trivial
pelo cadastro da estrutura, que é a base de atribuição de todas as métricas
futuras. Modelo e contratos já decididos: PRD em `initial-spec/initial-spec.md`,
API em `api/openapi.yaml`, UX em `prototype/`. Sem eventos/métricas, sem
auth/RBAC, sem ADO real — tudo fixture/in-memory.

## Goals / Non-Goals

**Goals:**
- Domínio de cadastro puro (sem Spring) com as invariantes do PRD.
- Persistência atrás de uma porta única (`StructureRepositoryPort`) com adapter
  in-memory, permitindo trocar por Postgres/ADO depois sem tocar domínio/app.
- Endpoints e telas de Admin (Estrutura, Identidades, Repositórios) fiéis ao
  `api/openapi.yaml` e ao `prototype/`.

**Non-Goals:**
- Contas/login/RBAC (S2); eventos/métricas/cobertura real (S3); ADO real (S9).
- Cobertura ainda é derivada de contagens semente (fixtures), não de eventos reais (S3).

## Decisions

- **Agregado por raiz de consistência.** `Vertical`, `Team`, `Person` são raízes
  com identidade própria; `TeamMembership` é entidade filha de `Person`.
  *Alternativa:* um único agregado "Org" — rejeitado por acoplar todo o cadastro
  num lock só e dificultar CRUD parcial.
- **Membership como lista datada em `Person` (as-of-event).** A pessoa carrega
  suas `TeamMembership { teamId, start, end? }`; "time atual" = membership sem
  `end`. Mover = `close(vigente, effective-1d) + open(novo, effective)`.
  *Alternativa:* campo `teamId` mutável — rejeitado por perder histórico, violando
  a regra de atribuição as-of-event do PRD.
- **Invariantes no domínio, não no controller.** 3 níveis fixos, 1 repo→1 time,
  1 membership aberto por pessoa, gestor = Person registrada — validados em
  fábricas/métodos do domínio, que lançam exceções de domínio. O web as traduz
  para 4xx (422/409).
- **Uma porta de saída (`StructureRepositoryPort`) no `application`, banco real no
  adapter.** O `adapter-out-persistence` provê `JpaStructureRepository` sobre
  **PostgreSQL** (JPA + Flyway), mapeando domínio↔entidade — o domínio/aplicação
  nunca veem JPA. Membership é `@ElementCollection`. *Alternativa:* in-memory —
  rejeitado: todo slice deve ser entrega durável e testável, sem pedaços em memória.
- **Identidades e repositórios como sub-recursos do cadastro.** `CommitterIdentity
  { identity, personId? }` e `Repository { key, teamId? }` moram na mesma porta.
  Cobertura = eventos atribuídos / total, derivada de contagens fixtures (campo
  `commitCount` semente), pois ainda não há eventos crus (S3).
- **Web = controllers finos + Thymeleaf.** Controllers convertem DTOs↔domínio;
  as telas server-renderizam o que o `prototype/` já desenhou. Sem lógica de
  negócio no adapter-in.
- **Bootstrap semeia fixtures** (as verticais/times/pessoas/identidades/repos do
  protótipo) na composição, para as telas terem conteúdo.

## Risks / Trade-offs

- **Echo slice coexiste ou sai.** → Mantemos o echo como smoke até o S2; a
  remoção fica explícita numa task, evitando quebrar testes/ArchUnit sem querer.
- **Cobertura "de mentira" (fixtures).** → Documentado como stub; o cálculo real
  sobre eventos chega no S3, reusando a mesma superfície de API.
- **Divergência entre as telas e o protótipo.** → O `prototype/` é a referência
  visual; as telas apenas o reproduzem, sem reinventar layout.
- **JaCoCo 70% em domain+application.** → As invariantes ricas do domínio dão
  cobertura natural; garantir testes de fábrica/membership para não furar o piso.

## Migration Plan

1. Implementar domínio + porta + adapter in-memory + web, mantendo o echo.
2. Semear fixtures no bootstrap; validar telas e endpoints.
3. `./gradlew build` verde (Spotless/Checkstyle/SpotBugs/JaCoCo/ArchUnit).
4. Remover o echo apenas quando o S2 assumir o papel de fatia de referência.

## Open Questions

- Manter o echo endpoint como health/smoke ou removê-lo já neste slice? (Proposta:
  manter até o S2.)
- `/admin/coverage` retorna cobertura global ou por dimensão neste slice? (Proposta:
  global agora; por dimensão quando houver eventos no S3.)
