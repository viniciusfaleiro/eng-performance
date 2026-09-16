package com.engperf.application.metrics;

import com.engperf.domain.metrics.MetricExplanation;
import java.util.Map;

/**
 * Explanations for what the UI shows that is not a single metric: a chart, a matrix, a breakdown.
 *
 * <p>These have no {@code MetricDefinition} to hang from — they describe a *visualisation*, and
 * what confuses a reader there is different: what an axis means, why a row is missing, why two
 * panels that look alike disagree. Same five-field shape as a metric explanation, so the UI needs
 * one modal and the build needs one completeness rule.
 */
final class ViewExplanations {

  private ViewExplanations() {}

  private static final Map<String, MetricExplanation> VIEWS =
      Map.of(
          "trend",
          new MetricExplanation(
              "A evolução da métrica escolhida ao longo dos últimos períodos, um ponto por"
                  + " período.",
              "O mesmo motor que calcula os cards — cada ponto é o card daquele período.",
              "Os períodos completos dentro da janela, na frequência selecionada.",
              "O período corrente, quando ainda não terminou — ele pareceria uma queda que é só"
                  + " falta de dias.",
              "Na frequência semanal, 12 pontos cobrem as últimas 12 semanas. Trocar para mensal"
                  + " não muda o cálculo, muda o tamanho do balde."),
          "heatmap",
          new MetricExplanation(
              "Uma matriz de entidades por métricas, colorida pela posição relativa dentro do"
                  + " grupo comparado.",
              "Os mesmos valores dos dashboards, recalculados para cada entidade.",
              "Times ou verticais do escopo atual, conforme a seleção.",
              "Métricas de volume (Commits e Pull Requests) não entram aqui: ranquear times por"
                  + " volume de código é um proxy fácil de manipular e não diz nada sobre entrega.",
              "Se três times têm cycle time de 20h, 40h e 90h, o verde vai para 20h e o vermelho"
                  + " para 90h. A cor é relativa ao grupo, não a um alvo absoluto."),
          "cycle_time_phases",
          new MetricExplanation(
              "O tempo de cada item é fatiado entre Espera, Ativo e Review conforme o estado em que"
                  + " ele esteve no board, momento a momento. As três fases somam o cycle time.",
              "O histórico de mudanças de estado do work item no Azure Boards — cada transição abre"
                  + " uma fatia e fecha a anterior.",
              "O sistema classifica cada estado pela categoria que o Azure Boards já atribui a ele:"
                  + " Proposed conta como Espera; InProgress conta como Review quando o nome do"
                  + " estado contém review, testing, qa ou verify, e como Ativo caso contrário;"
                  + " Completed, Resolved e Removed encerram a contagem. Quando a categoria não"
                  + " está disponível, a classificação cai para o nome do estado, com as mesmas"
                  + " palavras-chave (além de blocked, hold, waiting, ready, backlog, to do e new"
                  + " para Espera).",
              "O tempo no backlog antes do primeiro estado de trabalho é ignorado — ele entra no"
                  + " Lead Time (fluxo), não aqui. A Espera conta apenas a ociosidade entre o"
                  + " início do trabalho e a conclusão.",
              "Um item criado dia 1º, iniciado dia 5, parado em 'Blocked' dos dias 7 a 9, em 'Code"
                  + " Review' dos dias 9 a 10 e concluído dia 10: Ativo 48h, Espera 48h, Review"
                  + " 24h — cycle time de 120h. Os 4 dias entre criação e início não aparecem em"
                  + " nenhuma fase.\n\nPara o time usar bem: se o board tem um estado chamado"
                  + " 'Homologação', ele cai em Ativo, não em Review, porque nenhuma das"
                  + " palavras-chave aparece no nome. Renomear para 'Em homologação (QA)' passa a"
                  + " classificá-lo como Review."),
          "type_distribution",
          new MetricExplanation(
              "Como o tempo do período se dividiu entre tipos de trabalho — feature, bug, dívida"
                  + " técnica, manutenção.",
              "Work items concluídos, pelo tipo registrado no Azure Boards.",
              "Itens concluídos no período, com o tempo rateado quando houve concorrência.",
              "Itens sem tipo classificado entram como Outros, não são descartados.",
              "42% feature, 22% bug, 15% dívida técnica. A soma é sempre 100% do tempo medido."),
          "coverage",
          new MetricExplanation(
              "Quanto dos eventos do período o sistema conseguiu atribuir a uma pessoa e a um"
                  + " time.",
              "Todos os eventos brutos ingeridos do Azure DevOps.",
              "Numerador: eventos atribuídos. Denominador: todos os eventos do período.",
              "Nada é excluído — é justamente o não-atribuído que este número existe para expor.",
              "9 de 10 eventos atribuídos → 90%. Os 10% restantes são identidades de commit sem"
                  + " pessoa vinculada, e eles não aparecem em nenhum número de time."),
          "individual",
          new MetricExplanation(
              "A contribuição de uma pessoa, para conversa de coaching entre ela e seu gestor.",
              "Os mesmos eventos dos dashboards, filtrados por essa pessoa.",
              "Eventos atribuídos a ela no período de cada seção.",
              "Comparação com pessoas de outros times, e qualquer forma de ranking público — a"
                  + " plataforma mede para melhorar o sistema, não para vigiar pessoas.",
              "Throughput de 6 itens diz o que passou pelas mãos dela, não se ela é melhor que"
                  + " alguém. Sem o contexto do time, o número isolado engana."),
          "ado_stats",
          new MetricExplanation(
              "O que a última sincronização trouxe do Azure DevOps, por repositório.",
              "Os eventos gravados na ingestão: commits, PRs, deploys e work items.",
              "Tudo que foi ingerido desde a marca da última sincronização.",
              "Repositórios não cadastrados em Admin → Repositórios: eles nunca são consultados.",
              "1.200 commits e 180 PRs num repositório e zero em outro geralmente significa"
                  + " cadastro faltando, não repositório parado."));

  static Map<String, MetricExplanation> all() {
    return VIEWS;
  }
}
