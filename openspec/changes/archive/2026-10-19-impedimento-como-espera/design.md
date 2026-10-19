## Context

`WorkItemFlow.of(...)` (`adapters/out-ado/.../WorkItemFlow.java:38-98`) reconstrói o fluxo de um work
item a partir das transições de `System.State`, classificadas em `Segment` por `StateClassifier`, e
`fill(detail)` (`:100-123`) grava `active_h`/`review_h`/`wait_h` mais `num`/`den` (Flow Efficiency),
`hours` e `spans`. As três fases do cycle time (`MetricCatalog.PHASES`, `:48`; definições em
`:156-186`) leem exatamente essas chaves, com a mesma população (`completed`) do Cycle Time ao lado,
e `flow_efficiency` (`:202-211`) lê `num`/`den`. **Um único overlay no laço de `WorkItemFlow.of`
corrige as quatro leituras de uma vez** — nada a mudar em `MetricCatalog`, `Aggregations`,
`MetricsEngine` ou `PhaseSlice`.

O board do time não tem coluna de bloqueio: o sinal é o campo `Impediment` (≠ "None"/vazio) e/ou a
tag `Impedimento:<motivo>`. O endpoint `/_apis/wit/workitems/{id}/updates`, já buscado **por item**
para as transições de estado (`AdoEventSource.java:288-291`), traz o histórico de qualquer campo e
das tags — então o sinal não custa requisição nenhuma.

`detail` é uma coluna `jsonb` livre (`V3__events.sql:4-18`) desserializada para `Map<String,String>`
(`RawEventEntity.java:43-45`); nenhum filtro por `detail` acontece em SQL — o motor lê os eventos do
tipo e filtra em memória (`WorkTypeFilter`, `MetricsEngine.restrict`). Chave nova em `detail`:
custo zero de schema e de query.

## Goals / Non-Goals

**Goals:**
- Tempo impedido deixa de ser contado como trabalho: sai de Ativo/Review, entra em Espera — o que
  corrige simultaneamente Flow Efficiency e a quebra do cycle time.
- Espera abre uma quebra por tipo de impedimento que **fecha** com o número do card.
- Convenção configurável em Admin, com default semeado e desligamento por valor vazio.
- Painel individual deixa de contar tempo impedido como trabalho do liderado.
- Botões "i" descrevem a regra nova em todos os números que ela move.

**Non-Goals:**
- Métrica nova no catálogo, card novo, quarta fase.
- Mudar WIP/ocupação (um item bloqueado continua em andamento).
- Mudar `StateClassifier`.
- Reprocessar histórico automaticamente.
- Validar motivos contra taxonomia fechada.

## Decisions

### D1. Overlay no laço de `WorkItemFlow.of`, não um `Segment` novo

Alternativa descartada: introduzir `Segment.IMPEDED` e classificar o intervalo. Isso obrigaria a
decidir o que fazer com a quarta fase em `PHASES`, em `PhaseSlice`, no DTO e na barra da tela — e a
decisão de produto é que o impedimento **se funde na Espera**, sem fase nova. O overlay mantém a
classificação de estado como base e só move horas entre acumuladores:

```
working(seg):   impededMs = timeline.overlapMs(from,to)
                activeMs/reviewMs += ms - impededMs
                waitMs            += impededMs
WAITING:        waitMs += ms        (sem overlapMs: já é 100% espera)
```

Convenção vazia ⇒ timeline vazia ⇒ `impededMs == 0` ⇒ bytes idênticos aos de hoje. É o que torna a
mudança segura para quem não usa o sinal.

### D2. Atribuição do motivo cobre a Espera inteira, e o residual é derivado

O motivo tem de ser atribuído **também** ao impedimento que ocorreu dentro de um estado já WAITING —
senão a quebra não fecha com o card. Mas esse tempo **não** pode ser somado a `wait_h` outra vez
(D1). Logo são duas contas distintas: `wait_h` (quanto) e `impeded:<motivo>` (de quem é a culpa),
com a invariante `Σ impeded:* ≤ wait_h` e o residual `wait_h − Σ impeded:*` exibido como
"Fila do board". O residual é **derivado na leitura**, nunca gravado — pelo mesmo
raciocínio já registrado em `WorkDistribution.java:167-169`: um acumulador paralelo é a segunda
verdade que faz a lista não fechar com o próprio card. Pela mesma razão, **não** existe chave de
total (`impeded_h`): a soma dos motivos é o total.

### D3. Chaves com prefixo em `detail`, não CSV

`work_types` usa CSV porque as keys são de um enum fechado. Motivo é texto livre do time e pode
conter `,` e `:`, o que exigiria escapar. Então: uma chave por motivo.

- `impeded:<motivo>` → horas (motivo normalizado: `strip()` + lowercase).
- `impeded:?` → bucket reservado "Sem motivo" (campo marcado, sem tag). A normalização remove `?`
  das tags, então não há colisão possível com um motivo real.
- `impeded_label:<motivo>` → grafia original, para a legenda.
- `impeded_spans` → `"from:to,from:to"`, mesmo formato de `spans` (ver D5).

Ler é varrer as chaves com o prefixo — o `Map<String,String>` já está em memória.

### D4. A quebra viaja no payload de Fluxo que já existe

Alternativas: endpoint novo por métrica (`/api/metrics/waiting_time/impediments`) ou genérico
(`?by=`). Ambos criam superfície para um caso só. O card de fases vive do payload de
`/api/dashboards/flow` nas três telas em que aparece (`index.html:1277`, `:1381`, `:1516`), já
buscado uma vez por `ensureFlow()` (`:989-1009`); a quebra entra ali, calculada por uma classe nova
`ImpedimentBreakdown` que reusa `MetricsEngine.match`/`inBucket` e o predicado de
`MetricsEngine.restrict(...)` — **a mesma população do `waiting_time`**, do mesmo jeito que
`MetricsDrilldown.items` (`MetricsDrilldown.java:39-52`). RBAC de nó e filtro de tipo de trabalho
vêm de graça do controller que já existe.

### D5. Painel individual: subtrair de `spans` na leitura, nunca na gravação

A distribuição individual **não** usa `hours` — prorrateia os intervalos de `spans`
(`WorkDistribution.java:108-133` via `InProgressSpans`); `hours` é fallback de evento antigo
(`:118-120`). E `spans` é a mesma chave que `wip` consome (`MetricCatalog.java:131`,
`Occupation.INTERVAL`). Furar `spans` na ingestão tiraria o item bloqueado do WIP — mudança de
semântica que ninguém pediu. Então a ingestão grava `impeded_spans` **ao lado** de `spans`, e
`WorkDistribution` subtrai um do outro na leitura. `wip` fica intacto por construção.

### D6. Álgebra de intervalos no `domain`

União, interseção e diferença de intervalos são necessárias no adapter (overlay, D1) e na aplicação
(D5). Duas implementações divergiriam. Vai para `core/domain/metrics/`, ao lado de
`InProgressSpans.java`, que já é exatamente esse tipo de utilitário puro — adapter→domain e
application→domain são dependências permitidas, ArchUnit não é tensionado.

### D7. Convenção clonando `AiConvention`, migração `V9`

`ImpedimentConvention(fieldName, tagPrefix)` em `domain.config`, porta + use case + entity + repo +
endpoint de admin + fixture, tudo no molde de `AiConvention` — é o padrão que o repositório já tem
para "configuração de plataforma lida pela ingestão", e `AdoEventSource` já recebe
`PlatformConfigUseCase` por construtor (nenhuma porta nova).

A migração é **`V9__impediment_convention.sql`**: hoje existem `V1`–`V6` e `V8__account_last_login`
(o `V7` nunca existiu) e o Flyway roda sem `out-of-order`
(`app/bootstrap/src/main/resources/application.yml:14`) — um `V7` novo quebraria o `migrate` em
qualquer banco que já aplicou o V8.

### D8. Cauda longa de motivos resolvida na tela

Tag é texto livre: um time pode gerar dezenas de motivos. O agrupamento dos menores em "Outros"
fica na tela (teto de ~8 fatias), não na ingestão — o dado cru continua completo em `detail`, e a
decisão de apresentação pode mudar sem reprocessar nada.

## Risks / Trade-offs

- **O histórico já sincronizado não muda** → números de períodos antigos continuam inflados até um
  reprocessamento manual; a mudança é visível na comparação período-a-período. Dizer isso na
  explicação ("i") da métrica é parte da entrega.
- **Overlay com convenção errada (nome de campo que não é o *reference name*)** → nada é detectado
  e o resultado é igual ao de hoje, silenciosamente. Mitigação: a linha de cobertura do sinal no
  drawer da Espera ("N de M itens têm sinal") é o que revela a configuração errada; e o texto do
  Admin diz que o campo é o reference name (ex. `Custom.Impediment`).
- **Time que não marca impedimento parece ótimo** → é o incentivo invertido do produto inteiro.
  Mitigação: a mesma linha de cobertura, no mesmo lugar onde o número é lido.
- **Tag como texto livre** → cardinalidade alta e motivos quase-duplicados ("infra" / "Infra " /
  "infraestrutura"). Mitigação parcial: normalização (strip + lowercase) e agrupamento em "Outros".
  Sinonímia real fica para o time resolver na convenção dele — validar aqui seria taxonomia fechada,
  que é non-goal.
- **Quebra que não fecha com o card** se alguém gravar o residual como acumulador → mitigado por
  construção (D2: residual derivado) e por teste de invariante `Σ impeded:* ≤ wait_h`.
- **Painel individual com menos horas que antes** → o gestor pode ler como "a pessoa trabalhou
  menos". Mitigação: a frase sobre tempo impedido na explicação das horas, exigida pela spec de
  `individual-dashboard`.
- **Cobertura JaCoCo (70/60 em domain+application)** → a álgebra de intervalos no domain precisa de
  teste de borda (intervalos adjacentes, contidos, vazios), senão derruba o gate.

## Migration Plan

1. `V9__impediment_convention.sql` cria a tabela; a aplicação funciona com ela vazia
   (`(null, null)` = overlay desligado) — **deploy sem janela**.
2. `AdminFixtures` semeia o default (`Impediment` / `Impedimento:`) idempotentemente no primeiro
   boot.
3. Sem o passo de reprocessamento, a correção vale para o que for sincronizado a partir do deploy;
   reprocessar o backfill é operação manual já existente, decisão do usuário.
4. Rollback: limpar os dois campos da convenção desliga o comportamento sem deploy; reverter o
   código não exige reverter a migração (tabela inerte).

## Open Questions

- Qual o *reference name* real do campo no board do time (`Impediment` ou `Custom.Impediment`)? O
  default semeado é um palpite informado e é editável em Admin — a verificação é o passo 4 da
  checagem ponta a ponta.
- Reprocessar o histórico depois do deploy, para que os períodos anteriores fiquem comparáveis? Fora
  do escopo desta change; depende de o usuário querer a quebra retroativa.
