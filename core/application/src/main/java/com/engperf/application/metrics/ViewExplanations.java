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
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
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
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "Se três times têm cycle time de 20h, 40h e 90h, o verde vai para 20h e o vermelho"
                  + " para 90h. A cor é relativa ao grupo, não a um alvo absoluto."),
          "cycle_time_phases",
          new MetricExplanation(
              "O tempo de cada item é fatiado entre Espera, Ativo e Review conforme o estado em que"
                  + " ele esteve no board, momento a momento — e, por cima disso, o tempo em que o"
                  + " item estava marcado como impedido conta como Espera, mesmo que ele estivesse"
                  + " numa coluna de trabalho. As três fases somam o cycle time.",
              "O histórico de mudanças de estado do work item no Azure Boards — cada transição abre"
                  + " uma fatia e fecha a anterior.",
              "O sistema classifica cada estado pela categoria que o Azure Boards já atribui a ele:"
                  + " Proposed conta como Espera; InProgress conta como Review quando o nome do"
                  + " estado contém review, testing, qa ou verify, e como Ativo caso contrário;"
                  + " Completed, Resolved e Removed encerram a contagem. Quando a categoria não"
                  + " está disponível, a classificação cai para o nome do estado, com as mesmas"
                  + " palavras-chave (além de blocked, hold, waiting, ready, backlog, to do e new"
                  + " para Espera). Em seguida vem o impedimento: o trecho de um intervalo Ativo"
                  + " ou Review em que o item estava impedido sai dessa fase e entra na Espera —"
                  + " o impedimento é sobreposição, não uma quarta fase. Trecho que já era Espera"
                  + " por estado fica como está, para não contar a mesma hora duas vezes.",
              "O tempo no backlog antes do primeiro estado de trabalho é ignorado — ele entra no"
                  + " Lead Time (fluxo), não aqui. A Espera conta apenas a ociosidade entre o"
                  + " início do trabalho e a conclusão.",
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "Um item criado dia 1º, iniciado dia 5, parado em 'Blocked' dos dias 7 a 9, em 'Code"
                  + " Review' dos dias 9 a 10 e concluído dia 10: Ativo 48h, Espera 48h, Review"
                  + " 24h — cycle time de 120h. Os 4 dias entre criação e início não aparecem em"
                  + " nenhuma fase.\n\nMesmo item, mas sem a coluna 'Blocked': ele fica em"
                  + " 'Active' do dia 5 ao 9 e, dos dias 7 a 9, com a tag 'Impedimento:infra'. O"
                  + " resultado é o mesmo — 48h de Espera —, porque o impedimento é lido onde ele"
                  + " aconteceu.\n\nPara o time usar bem: marcar o impedimento é o que faz esse"
                  + " tempo aparecer, e escrever o motivo na tag ('Impedimento:infra') é o que"
                  + " nomeia a fatia quando alguém abre a Espera. Marcar só o campo de"
                  + " impedimento conta o tempo, mas cai em 'Sem motivo'. E o estado chamado"
                  + " 'Homologação' cai em Ativo, não em Review, porque nenhuma das palavras-chave"
                  + " aparece no nome: renomear para 'Em homologação (QA)' passa a classificá-lo"
                  + " como Review."),
          "type_distribution",
          new MetricExplanation(
              "Como o tempo do período se dividiu entre tipos de trabalho — feature, bug, dívida"
                  + " técnica, manutenção.",
              "Work items concluídos, pelo tipo registrado no Azure Boards.",
              "Itens concluídos no período, com o tempo rateado quando houve concorrência e sem o"
                  + " tempo em que o item estava marcado como impedido — item parado não é trabalho"
                  + " indo a lugar nenhum.",
              "Itens sem tipo classificado entram como Outros, não são descartados. O tempo"
                  + " impedido não entra em tipo nenhum: ele aparece como Espera no dashboard de"
                  + " Fluxo.",
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "42% feature, 22% bug, 15% dívida técnica. A soma é sempre 100% do tempo medido."),
          "impediment_reasons",
          new MetricExplanation(
              "De que a Espera do período foi feita: cada motivo de impedimento que o time"
                  + " escreveu, o que foi impedimento sem motivo declarado, e o que foi fila do"
                  + " board sem nenhum impedimento marcado.",
              "Work items do Azure Boards: o campo de impedimento e as tags com o prefixo"
                  + " configurado em Admin, lidos do histórico de mudanças do item.",
              "O motivo é o texto depois do prefixo da tag — 'Impedimento:infra' vira 'infra',"
                  + " ignorando maiúsculas e espaços, de modo que a mesma palavra escrita de"
                  + " formas diferentes é um motivo só. 'Sem motivo' é o tempo impedido sem tag que"
                  + " o nomeie (o campo marcado, tipicamente). 'Fila do board' é o que"
                  + " sobra da Espera, e é calculado por subtração — nunca acumulado em paralelo,"
                  + " para não poder discordar do total.",
              "Impedimento registrado antes do trabalho começar, que fica no backlog e não entra"
                  + " em fase nenhuma. As fatias descrevem a soma das horas de espera da população"
                  + " do período — não são uma divisão do card de Espera, que é mediana: mediana"
                  + " não é soma de nada, e fingir o contrário inventaria uma conta que não fecha.",
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "De 40h de espera no período: 18h 'infra' (45%), 6h 'aprovação' (15%), 4h 'Sem"
                  + " motivo' (10%) e 12h de fila (30%).\n\nA linha de cobertura ao lado diz em"
                  + " quantos itens havia algum sinal de impedimento. Se ela diz 0 de 20, a"
                  + " Espera é toda fila — mas também é possível que o time simplesmente não"
                  + " marque impedimento, e aí o número bonito não significa nada."),
          "coverage",
          new MetricExplanation(
              "Quanto dos eventos do período o sistema conseguiu atribuir a uma pessoa e a um"
                  + " time.",
              "Todos os eventos brutos ingeridos do Azure DevOps.",
              "Numerador: eventos atribuídos. Denominador: todos os eventos do período.",
              "Nada é excluído — é justamente o não-atribuído que este número existe para expor.",
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "9 de 10 eventos atribuídos → 90%. Os 10% restantes são identidades de commit sem"
                  + " pessoa vinculada, e eles não aparecem em nenhum número de time."),
          "individual",
          new MetricExplanation(
              "A contribuição de uma pessoa, para conversa de coaching entre ela e seu gestor.",
              "Os mesmos eventos dos dashboards, filtrados por essa pessoa.",
              "Eventos atribuídos a ela no período de cada seção.",
              "Comparação com pessoas de outros times, e qualquer forma de ranking público — a"
                  + " plataforma mede para melhorar o sistema, não para vigiar pessoas.",
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "Throughput de 6 itens diz o que passou pelas mãos dela, não se ela é melhor que"
                  + " alguém. Sem o contexto do time, o número isolado engana."),
          "ado_stats",
          new MetricExplanation(
              "O que a última sincronização trouxe do Azure DevOps, por repositório.",
              "Os eventos gravados na ingestão: commits, PRs, deploys e work items.",
              "Tudo que foi ingerido desde a marca da última sincronização.",
              "Repositórios não cadastrados em Admin → Repositórios: eles nunca são consultados.",
              "Cada card da visão diz a que data ou intervalo o item dele pertence; a visão"
                  + " não tem recorte próprio de tempo além do período selecionado.",
              "1.200 commits e 180 PRs num repositório e zero em outro geralmente significa"
                  + " cadastro faltando, não repositório parado."));

  static Map<String, MetricExplanation> all() {
    return VIEWS;
  }
}
