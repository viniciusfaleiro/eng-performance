package com.engperf.application.metrics;

import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricExplanation;
import java.util.List;
import java.util.Map;

/**
 * The text a reader gets when asking how a number was produced.
 *
 * <p>Lives beside {@link MetricCatalog} rather than inside it only because the catalog is already
 * near the file-length ceiling; conceptually this is part of the catalog, and {@link #attach} joins
 * the two. That distance is the reason {@code MetricCatalogExplanationTest} exists: nothing here
 * stops a text from drifting away from the calculation it describes, so the least the build can do
 * is refuse a metric that nobody explained.
 *
 * <p>Every example uses invented numbers. They are there to make the statistic concrete — a median
 * is not an average, a ratio needs its denominator named — because that is the part readers get
 * wrong, not the definition.
 */
final class MetricExplanations {

  private MetricExplanations() {}

  private static final String AS_OF_EVENT =
      "O período fica com o time em que a pessoa estava quando o evento aconteceu. Se ela mudar de"
          + " time depois, os números passados não se movem junto.";

  private static final Map<String, MetricExplanation> TEXTS =
      Map.ofEntries(
          // ---- DORA ----
          Map.entry(
              "deploy_freq",
              new MetricExplanation(
                  "Quantos deploys chegaram a produção no período, divididos pelo número de dias —"
                      + " ou seja, a frequência média diária.",
                  "Execuções de pipeline do Azure Pipelines cujo stage de produção terminou.",
                  "Todo stage de produção concluído, tenha ele sucedido ou falhado.",
                  "Stages que não são de produção, e execuções ainda em andamento ou puladas.",
                  "14 deploys em 7 dias → 2,0 por dia.")),
          Map.entry(
              "lead_time",
              new MetricExplanation(
                  "A mediana do tempo entre o primeiro commit de uma mudança e ela estar em"
                      + " produção.",
                  "Deploys, com o tempo medido desde o commit mais antigo que entrou nele.",
                  "Deploys de produção que concluíram.",
                  "Deploys sem commit associado — sem ponto de partida, não há o que medir.",
                  "Cinco deploys levaram 8h, 12h, 20h, 30h e 90h. A mediana é 20h — o valor do"
                      + " meio. A média seria 32h, puxada pelo deploy de 90h.")),
          Map.entry(
              "cfr",
              new MetricExplanation(
                  "A porcentagem dos deploys que falharam ou exigiram correção logo depois.",
                  "Deploys, classificados pelo resultado do stage de produção.",
                  "Numerador: deploys com falha. Denominador: todos os deploys do período.",
                  "Deploys que não chegaram a executar o stage de produção.",
                  "3 deploys com falha em 25 deploys → 3 ÷ 25 = 12%.")),
          Map.entry(
              "mttr",
              new MetricExplanation(
                  "A mediana do tempo entre um deploy falhar e o serviço voltar ao normal.",
                  "Deploys com falha, medindo até o deploy seguinte que restaurou o serviço.",
                  "Falhas que já foram restauradas.",
                  "Falhas ainda não resolvidas — elas não têm duração final, e incluí-las com o"
                      + " tempo parcial faria o indicador parecer melhor do que é.",
                  "Três restaurações levaram 30min, 2h e 6h. A mediana é 2h.")),
          // ---- Fluxo ----
          Map.entry(
              "cycle_time",
              new MetricExplanation(
                  "A mediana do tempo que um work item leva desde que alguém começa a trabalhar"
                      + " nele até ser concluído. Horas corridas, não horas úteis.",
                  "Work items do Azure Boards, pelo histórico de mudanças de estado.",
                  "Apenas work items que chegaram a um estado de conclusão dentro do período.",
                  "Itens ainda abertos, por mais antigos que sejam — eles entram no WIP, não aqui.",
                  "Cinco itens levaram 10h, 20h, 30h, 40h e 50h. A mediana é 30h. A média também"
                      + " daria 30h, mas viraria 50h se um único item tivesse levado 150h — e é"
                      + " por isso que usamos mediana.")),
          Map.entry(
              "throughput",
              new MetricExplanation(
                  "Quantos work items foram concluídos no período. É contagem de itens, não de"
                      + " commits nem de PRs.",
                  "Work items do Azure Boards que entraram em estado de conclusão.",
                  "Todo item concluído no período, qualquer que seja o tipo.",
                  "Itens em andamento, e itens concluídos fora da janela do período.",
                  "O time concluiu 4 features, 3 bugs e 1 tarefa técnica na semana →"
                      + " throughput de 8 itens.")),
          Map.entry(
              "flow_lead_time",
              new MetricExplanation(
                  "A mediana do tempo entre a criação do work item e sua conclusão — inclui o tempo"
                      + " que ele passou na fila, antes de alguém começar.",
                  "Work items do Azure Boards, da data de criação à de conclusão.",
                  "Apenas itens concluídos no período.",
                  "Itens em aberto. É a diferença para o Cycle Time: este conta a espera, o Cycle"
                      + " Time começa a contar quando o trabalho começa.",
                  "Um item criado dia 1º e concluído dia 11 tem lead time de 10 dias. Se o trabalho"
                      + " só começou dia 9, o cycle time é 2 dias — os outros 8 foram fila.")),
          Map.entry(
              "wip",
              new MetricExplanation(
                  "Quantos work items estavam em andamento no período. É uma contagem de itens.",
                  "Work items do Azure Boards em estado de trabalho ativo.",
                  "Itens que estiveram em andamento em algum momento do período.",
                  "Itens concluídos e itens que nunca saíram da fila.",
                  "Se 9 itens estiveram em andamento na semana, o WIP é 9 — mesmo que alguns"
                      + " tenham sido concluídos depois. Contagem, não soma de horas.")),
          Map.entry(
              "flow_efficiency",
              new MetricExplanation(
                  "Do tempo total que um item levou, a porcentagem em que ele esteve efetivamente"
                      + " sendo trabalhado — o resto é espera.",
                  "Work items do Azure Boards, comparando tempo ativo com tempo total.",
                  "Numerador: horas em estado ativo. Denominador: horas entre início e conclusão.",
                  "Itens sem histórico de estado suficiente para separar ativo de espera.",
                  "Um item levou 40h no total, das quais 10h de trabalho ativo →"
                      + " 10 ÷ 40 = 25%. Os outros 75% foram fila, revisão parada ou bloqueio.")),
          Map.entry(
              "pr_review_time",
              new MetricExplanation(
                  "A mediana do tempo entre abrir um pull request e ele receber a primeira"
                      + " revisão.",
                  "Pull requests do Azure Repos, com os timestamps dos votos de revisão.",
                  "PRs que receberam ao menos uma revisão.",
                  "PRs sem revisão nenhuma — não há o que medir, e contá-los como zero diria o"
                      + " oposto da verdade.",
                  "Quatro PRs esperaram 1h, 3h, 5h e 40h. A mediana é 4h.")),
          Map.entry(
              "pr_size",
              new MetricExplanation(
                  "A mediana de linhas alteradas por pull request, somando adições e remoções.",
                  "Pull requests do Azure Repos, pelos commits que fazem parte deles.",
                  "PRs concluídos no período.",
                  "PRs sem informação de alteração de linhas.",
                  "PRs de 20, 80, 200 e 900 linhas → mediana de 140 linhas.")),
          // ---- Volume ----
          Map.entry(
              "commit_count",
              new MetricExplanation(
                  "Quantos commits foram feitos no período. É volume de trabalho de código, não"
                      + " medida de entrega.",
                  "Commits dos repositórios cadastrados.",
                  "Todos os commits do período, sem nenhum filtro de conclusão.",
                  "Nada é excluído — e é justamente por isso que este número não é comparável ao"
                      + " Throughput, que só conta o que foi entregue.",
                  "180 commits na semana é apenas isso: 180 commits. Não diz quanto valor chegou"
                      + " ao usuário.")),
          Map.entry(
              "pr_count",
              new MetricExplanation(
                  "Quantos pull requests houve no período. Volume de trabalho de código, como"
                      + " contexto para o Throughput.",
                  "Pull requests dos repositórios cadastrados.",
                  "Todos os PRs do período.",
                  "Nada é excluído. Um PR grande e um PR de uma linha contam igual — leia junto"
                      + " com o PR Size.",
                  "34 PRs na semana. Se o Throughput foi 8 itens, são ~4 PRs por item entregue.")),
          // ---- Fases do cycle time ----
          Map.entry(
              "waiting_time",
              new MetricExplanation(
                  "A mediana do tempo que os itens passaram parados na fila, antes de alguém"
                      + " começar a trabalhar neles.",
                  "Work items do Azure Boards, pelo tempo em estados de espera.",
                  "Itens concluídos que passaram por estado de espera.",
                  "O tempo depois que o trabalho começou — isso é Ativo ou Review.",
                  "Um item criado segunda e iniciado quinta tem 3 dias de espera. É a fase que"
                      + " normalmente domina o cycle time e a que menos depende de quem executa.")),
          Map.entry(
              "active_time",
              new MetricExplanation(
                  "A mediana do tempo em que os itens estiveram efetivamente em desenvolvimento.",
                  "Work items do Azure Boards, pelo tempo em estados de trabalho ativo.",
                  "Itens concluídos que passaram por estado ativo.",
                  "Fila e revisão — elas têm suas próprias fases.",
                  "Se o cycle time é 40h e o ativo é 10h, as outras 30h foram espera e revisão.")),
          Map.entry(
              "review_time",
              new MetricExplanation(
                  "A mediana do tempo que os itens passaram aguardando ou recebendo revisão.",
                  "Work items do Azure Boards, pelo tempo em estados de revisão.",
                  "Itens concluídos que passaram por estado de revisão.",
                  "Tempo de fila inicial e de desenvolvimento.",
                  "6h em revisão num item de 40h é 15% do ciclo. Compare com o PR Review Time, que"
                      + " mede a mesma espera do lado do pull request.")),
          // ---- Coortes do dashboard de IA ----
          Map.entry(
              "code_cycle_time",
              new MetricExplanation(
                  "A mediana do tempo de ciclo dos pull requests. Existe para comparar as coortes"
                      + " com e sem IA no dashboard de IA.",
                  "Pull requests do Azure Repos, da abertura à conclusão.",
                  "PRs concluídos com tempo de ciclo medido.",
                  "PRs em aberto. E atenção: isto é o ciclo do PR, não do work item — o Cycle"
                      + " Time do dashboard de Fluxo mede outra coisa e costuma ser maior.",
                  "PRs de 4h, 9h e 30h → mediana de 9h.")),
          Map.entry(
              "code_throughput",
              new MetricExplanation(
                  "Quantos pull requests foram concluídos no período. Serve de denominador para as"
                      + " comparações do dashboard de IA.",
                  "Pull requests do Azure Repos que foram concluídos.",
                  "PRs concluídos no período.",
                  "PRs abandonados ou ainda abertos. É a diferença para a métrica Pull Requests"
                      + " do Fluxo, que conta todos sem filtro.",
                  "23 PRs concluídos na semana.")),
          // ---- IA ----
          Map.entry(
              "ai_share",
              new MetricExplanation(
                  "A porcentagem dos commits que foram feitos com assistência de IA.",
                  "Commits, classificados pela convenção configurada em Admin → Convenção de IA"
                      + " (um trailer, uma tag ou uma expressão regular na mensagem).",
                  "Numerador: commits que casam com a convenção. Denominador: todos os commits.",
                  "Nada é excluído do denominador — um commit sem marcação conta como sem IA.",
                  "34 commits marcados em 100 commits → 34%. Se o time não usa a convenção, o"
                      + " número fica baixo por falta de marcação, não por falta de uso.")),
          Map.entry(
              "ai_adoption",
              new MetricExplanation(
                  "A porcentagem das pessoas que usaram IA ao menos uma vez no período. Mede"
                      + " quantas pessoas adotaram, não quanto cada uma usa.",
                  "Commits, contando pessoas distintas em vez de commits.",
                  "Numerador: pessoas com ao menos um commit marcado. Denominador: pessoas com"
                      + " ao menos um commit no período.",
                  "Pessoas que não commitaram no período — elas não teriam como adotar.",
                  "7 pessoas usaram IA entre 12 que commitaram → 58%. Uma pessoa com 1 commit"
                      + " marcado conta igual a outra com 100.")),
          Map.entry(
              "ai_impact",
              new MetricExplanation(
                  "Quanto o cycle time dos PRs com IA difere do cycle time dos PRs sem IA, em"
                      + " porcentagem.",
                  "Duas coortes de pull requests: com e sem marcação de IA.",
                  "PRs de ambas as coortes que tenham cycle time medido.",
                  "Períodos em que uma das coortes está vazia — sem os dois lados não há"
                      + " comparação.",
                  "PRs sem IA com mediana de 100h e com IA de 81h → 19% mais rápido. É uma"
                      + " correlação observada, não prova de causa: tarefas mais simples podem"
                      + " atrair mais o uso de IA.")));

  /** Attribution works the same way for every metric, so it is said once, not sixteen times. */
  static String attribution() {
    return AS_OF_EVENT;
  }

  /** The definitions, each carrying its text. Keys without text stay unexplained on purpose. */
  static List<MetricDefinition> attach(List<MetricDefinition> definitions) {
    // copyOf, not toList: the catalog hands this list out, and an immutable one is the only kind
    // safe to share.
    return List.copyOf(
        definitions.stream()
            .map(d -> TEXTS.containsKey(d.key()) ? d.withExplanation(TEXTS.get(d.key())) : d)
            .toList());
  }

  static Map<String, MetricExplanation> texts() {
    return TEXTS;
  }
}
