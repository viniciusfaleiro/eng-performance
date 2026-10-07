## Context

`pr_size` é mediana sobre a chave de detalhe `lines`, gravada numa linha só do mapper:

```java
detail.put("lines", Long.toString(hasCounts ? lines : count));   // AdoMapper.java:95
```

`hasCounts` nunca é verdadeiro na prática. Medido com o payload gravado do org real: três commits
num PR → `lines = 3`. O card exibe contagem de commits rotulada como linhas.

A doc oficial 7.1 define `changeCounts` como *"Counts of the types of changes (edits, deletes, etc.)
included with the commit"* — itens, não linhas. E os dois exemplos do endpoint `Commits - Get` são
decisivos: **sem** `changeCount`, nenhum `changeCounts` na resposta; **com** `?changeCount=10`, ele
aparece, e traz `Add: 456` enquanto o array `changes` vem truncado em dez. Ou seja: o parâmetro é
obrigatório para obter a contagem, e a contagem é o total verdadeiro independente do truncamento.

Já existe uma busca por commit no sync: `CommitComments.full()` recarrega o commit quando o
comentário vem truncado, porque é no corpo cortado que vivem os trailers de IA.

## Goals / Non-Goals

**Goals:**

- Que o PR Size meça o tamanho da mudança, na unidade que a fonte realmente oferece.
- Que a ausência de medida se declare como ausência, e não como um número parecido.
- Que o commit seja buscado uma vez, mesmo quando duas coisas diferentes precisam dele.

**Non-Goals:**

- Linhas alteradas. Exigiria baixar os blobs de cada arquivo e diferenciar do nosso lado, com
  binário, arquivo grande e renomeação. Decidido fora de escopo com o usuário.
- Contar arquivos no evento de commit (hoje `AdoMapper.commit` não grava tamanho nenhum). Nenhuma
  métrica lê isso, e inventar a chave agora seria modelagem adiantada.

## Decisions

### 1. Arquivos, e o nome do dado muda junto

A chave de detalhe deixa de ser `lines` e passa a ser `files`; a unidade da métrica deixa de ser
"linhas" e passa a ser "arquivos".

Renomear a chave não é cosmético. Enquanto ela se chamar `lines`, qualquer leitura futura do banco,
de um dump ou de um log vai reafirmar a interpretação errada — e foi exatamente uma chave mentindo o
nome que sustentou o defeito por tanto tempo. O dado antigo gravado sob `lines` fica órfão de
propósito: ele é contagem de commits, não serve para nada, e ninguém deve conseguir lê-lo como
tamanho.

### 2. A contagem vem de uma chamada por commit, com o parâmetro explícito

`?changeCount=1&api-version=7.1` por commit. Um, e não zero, porque sem o parâmetro o campo não
aparece; e um, e não dez, porque o array `changes` não é usado — só o total, que vem certo de
qualquer forma.

Alternativa rejeitada: ler `changeCounts` da lista de commits do PR. O contrato do `GitCommitRef`
inclui o campo, mas o endpoint não tem parâmetro para pedi-lo e o payload gravado do org real não o
traz. Foi exatamente essa suposição que criou o defeito; repeti-la com outro endpoint seria trocar o
bug de lugar.

### 3. Uma chamada por commit serve a todos os propósitos

`CommitComments` passa a memoizar a busca por id de commit e a incluir `changeCount=1`. Assim o
commit que precisa do comentário completo **e** da contagem é buscado uma vez. O contador de
recargas que já existe passa a medir o total de buscas, então o custo real aparece no relatório de
sync em vez de ser estimado aqui.

Consequência assumida: a busca deixa de ser condicional. Hoje ela só acontece quando o comentário
vem truncado; agora acontece para todo commit de PR, porque a contagem nunca vem na lista. É uma
chamada a mais por commit, e é o preço de ter a medida.

### 4. Soma parcial não existe

Se a contagem de **qualquer** commit do PR falhar, o PR vai sem contagem. Uma soma faltando um
commit é indistinguível de uma medição legítima, e a mediana que a lê não tem como saber que está
baixa. É a mesma regra que a distribuição de trabalho e a eficiência de fluxo já seguem: sem dado é
sem dado.

### 5. O mapper continua puro

`AdoMapper.pullRequest` recebe a contagem já resolvida, em vez de ganhar um cliente HTTP. O mapper é
a única peça testável sem rede neste adapter, e é por isso que os testes dele são a referência da
forma dos payloads — dar-lhe uma chamada de rede destruiria essa propriedade.

## Risks / Trade-offs

- **O número vai subir.** De "mediana de commits por PR" para "mediana de arquivos por PR". Quem
  olhava o card antes estava lendo outra coisa; o histórico de leitura dele não se compara com o
  novo.

- **A cobertura não revela a medida faltando, e eu prometi que revelaria.** Verificado no app: um PR
  sem contagem cai da mediana e aparece no drill-down como não contado ("sem medida"), mas o card
  segue com 100% de cobertura. A razão é que cobertura mede **atribuição** — evento atribuído ao nó
  versus não atribuído —, que é o desenho declarado no CLAUDE.md, e vale para **toda** métrica de
  mediana (cycle time, lead time, as fases, PR review time, MTTR). A spec foi corrigida para dizer o
  que o código faz; tornar a cobertura ciente da medida seria mudar o significado dela em todo o
  catálogo, e isso é change própria. Enquanto não for, a lista de itens é o único lugar onde a
  exclusão aparece.

- **Sem reprocessar, o card fica sem dado para trás — para sempre.** O sync é incremental e pula PR
  fechado antes do watermark, então ele só preenche `files` nos PRs novos; os já ingeridos nunca são
  buscados de novo. Períodos passados não se recuperam com o tempo, só com o reprocessamento de 6
  meses. Escrevi o contrário na primeira versão deste documento ("nada de reprocessamento
  obrigatório") e estava errado: o Migration Plan abaixo era a parte correta.

- **Uma chamada a mais por commit de PR.** ~1.000 por repositório por sync com 200 PRs de cinco
  commits. Se virar problema, aparece como sync lento, não como número errado. O próximo passo
  natural seria cachear por commit **entre** syncs, já que a contagem de um commit nunca muda.

- **Pasta criada conta como item alterado.** O exemplo oficial lista `tree` ao lado de `blob` no
  array de mudanças, então um commit que cria diretórios conta um pouco mais que os arquivos que
  toca. Fica dito na explicação do card. Filtrar só `blob` exigiria transferir o array inteiro de
  mudanças e perderia o total verdadeiro quando ele vem truncado — trocaria um desvio pequeno e
  conhecido por um erro grande e silencioso.

## Migration Plan

1. Deploy.
2. O card fica sem dado, porque nenhum PR ingerido tem a contagem.
3. Rodar o sync. A partir daí os PRs novos trazem `files`; o histórico só se preenche com o
   reprocessamento, que já está pendente por outras razões.
4. **Rodar "Reprocessar 6 meses"** (Admin → Azure DevOps), que ignora o watermark e recarrega a
   janela aplicando o mapeamento atual. É a execução mais cara que esse botão já teve: agora cada
   commit de PR custa uma chamada a mais, ~1.000 por repositório com 200 PRs de cinco commits. Vale
   rodar fora do horário e ler o contador de recargas no relatório — é o número que esta change não
   conseguiu estimar.

## Open Questions

- **A cobertura devia contar medida faltando, e não só atribuição?** Hoje um PR atribuído sem
  contagem mantém a cobertura em 100% e desaparece da mediana sem aviso no card. Trocar isso seria
  coerente com "sem dado nunca é zero", mas muda o significado de cobertura para todas as métricas de
  mediana de uma vez, e o número de várias delas vai cair — vale decidir vendo quanto cai.
- Vale cachear a contagem por commit **entre** syncs? A contagem de um commit é imutável, então é o
  cache mais seguro que existe aqui — mas é tabela nova, e o custo atual ainda não foi medido em
  produção. Melhor decidir com o número do primeiro sync na mão.
