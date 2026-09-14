## Why

Registrar repositório é hoje 1-a-1, manual, sem nenhuma checagem contra o que realmente existe no
projeto do Azure DevOps — o admin digita chave/org/projeto de memória, um por um. Isso não escala
(um projeto com dezenas de repositórios exige dezenas de cadastros manuais) e diverge com o tempo
(um repo criado ou arquivado no ADO não se reflete no cadastro até alguém lembrar de mexer). O admin
quer dar org+projeto, deixar a plataforma descobrir o que precisa entrar/sair do cadastro, e só
então associar cada repo novo ao time certo — o resto (nome, org, projeto, e se possível o stage de
produção) a ferramenta já traz.

## What Changes

- **NEW** — dado um par (organização, projeto) informado pelo admin, o sistema autentica via o
  mesmo fluxo interativo de device-code já usado no sync (sem PAT), lista os repositórios Git desse
  projeto no ADO, e compara com o que já está cadastrado para esse mesmo (organização, projeto).
- **NEW** — o resultado é um **diff**: repositórios que existem no ADO mas não no cadastro (a
  inserir, sem time, sem stage — ficam iguais à criação manual de hoje) e repositórios cadastrados
  que não existem mais no ADO daquele projeto (a remover). O admin revisa o diff antes de aplicar —
  nada é inserido ou removido sem confirmação explícita.
- **NEW** — ao aplicar o diff, cada repositório novo entra com **tentativa de detecção automática
  do stage de produção**: o sistema olha os pipelines recentes daquele repo e procura, nos nomes de
  stage, o mesmo padrão já usado hoje como fallback de classificação de deploy (contém "prod" ou
  "prd"). Se achar exatamente um candidato, preenche; se não achar nenhum ou achar mais de um
  (ambíguo), deixa **vazio** — o admin corrige depois, igual já acontece hoje pra qualquer repo sem
  stage definido.
- Depois de aplicado, associar cada repositório novo ao time correto continua sendo o fluxo que já
  existe hoje (o seletor de time por linha, na aba Repositórios) — esta change não cria nenhuma UI
  nova para esse passo.
- O cadastro manual 1-a-1 continua existindo como está — a descoberta é um atalho complementar, não
  uma substituição.

## Non-goals

- **Não** faz descoberta global (todas as orgs/projetos de uma vez) — é sempre um (organização,
  projeto) por vez, informado explicitamente pelo admin, igual ao resto do produto (sem org-wide
  discovery automática por decisão do PRD).
- **Não** aplica o diff sozinho: a remoção em especial é uma ação destrutiva no cadastro (perde a
  associação de time daquele repo) e exige o clique de confirmação do admin, nunca acontece como
  efeito colateral de só olhar o diff.
- **Não** apaga nenhum `raw_event` histórico ao remover um repositório do cadastro — remove só a
  linha de cadastro (organização/projeto/time/stage); eventos já ingeridos permanecem no banco,
  igual já acontece hoje quando um admin remove um repo manualmente.
- **Não** tenta adivinhar o time de um repositório novo — fica sem time (fora do escopo DORA) até
  o admin associar, exatamente como um cadastro manual hoje.
- **Não** garante achar o stage de produção sempre — quando ambíguo ou ausente, fica vazio; não é
  um substituto pra revisão do admin, só reduz o trabalho manual no caso comum.

## Capabilities

### New Capabilities
- `repo-discovery`: dado um (organização, projeto), autentica no ADO, lista os repositórios reais
  do projeto, calcula o diff contra o cadastro, e aplica inserções/remoções sob confirmação do
  admin — incluindo a tentativa de detecção automática do stage de produção pra cada repo novo.

### Modified Capabilities
- `repository-mapping`: deixa de ser estritamente verdade que "there is no org-wide discovery" —
  descoberta por (organização, projeto) explícito passa a existir como atalho pro cadastro manual,
  que continua disponível e é o que a descoberta usa por baixo (mesmo registro, mesma regra de
  1 repositório → 1 time).

## Impact

- **Specs**: nova `openspec/specs/repo-discovery`; delta em `openspec/specs/repository-mapping`.
- **adapter-out-ado**: novo método no `AdoEventSourcePort` (ou porta nova dedicada) pra listar
  repositórios Git de um projeto (`_apis/git/repositories`) e, por repo, inspecionar builds/timeline
  recentes pra detectar o stage de produção — reaproveitando a mesma heurística de nome
  ("prod"/"prd") já usada em `AdoMapper.matchesProduction`.
- **application**: novo caso de uso de descoberta (autentica, lista, faz diff contra
  `StructureRepositoryPort.findRepositories()`, aplica inserções/remoções via as operações que
  `RepositoryUseCase` já expõe).
- **adapter-in-web**: novo(s) endpoint(s) em `RepositoryController` (ou controller próprio) pra
  iniciar a descoberta (device-code, como o sync já faz), consultar o diff, e aplicar; DTOs novos;
  `docs/api/openapi.yaml` atualizado.
- **UI** (`static/index.html`): aba Repositórios ganha a entrada de organização/projeto, o
  fluxo de login device-code (reaproveitando o componente visual já usado no sync), a tela de diff
  (a inserir / a remover) e o botão de aplicar.
