package com.engperf.application.metrics;

import com.engperf.domain.metrics.RawEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The coaching flags of one person's panel: which agreed conventions their own activity suggests
 * may be broken (see docs/convencoes-adocao-times.xlsx).
 *
 * <p>Lifted out of {@link IndividualDashboardService} because it is a self-contained reading of the
 * same events — heuristics that raise a flag or stay quiet, never a metric.
 */
final class ConventionFlags {

  private ConventionFlags() {}

  /**
   * Coaching flags: which agreed conventions this person's own activity suggests may be broken (see
   * docs/convencoes-adocao-times.xlsx). Heuristics over the person's events; only raised flags.
   */
  static List<ConventionFlag> of(
      List<RawEvent> commits,
      List<RawEvent> prs,
      List<RawEvent> workItems,
      List<RawEvent> reviewsReceived,
      WorkDistribution distribution) {
    // No activity at all → the identity is likely unmapped or the commit email diverges (Grupo A).
    if (commits.isEmpty() && prs.isEmpty() && workItems.isEmpty() && reviewsReceived.isEmpty()) {
      return List.of(
          new ConventionFlag(
              "1",
              "warn",
              "Convenções 1–2 · Identidade e estrutura",
              "Sem atividade atribuída no período",
              "Nenhum commit, PR ou work item chegou a este contribuidor. A identidade de commit"
                  + " pode não estar vinculada à Pessoa (Admin → Identidades) ou o e-mail de commit"
                  + " diverge do cadastrado. Confirme o git user.email do dev.",
              List.of("todas as métricas de pessoa", "cobertura")));
    }
    List<ConventionFlag> flags = new ArrayList<>();
    addIfPresent(flags, aiFlag(commits));
    addIfPresent(flags, prFlag(commits, prs));
    ConventionFlag board = boardFlag(commits, workItems);
    ConventionFlag shortItems = shortItemFlag(distribution);
    // Os dois falam da mesma convenção 21, e a tela indexa os alertas por código: dois com o mesmo
    // código fariam um apagar o outro na gaveta do catálogo. Quando há item de minutos, é ele que
    // tem o que dizer — "board sem transição aproveitável" é contradito pelo próprio dado que
    // permitiu medir os minutos.
    addIfPresent(
        flags, shortItems != null && board != null && "21".equals(board.code()) ? null : board);
    addIfPresent(flags, shortItems);
    addIfPresent(flags, reviewFlag(prs, reviewsReceived));
    return flags;
  }

  private static void addIfPresent(List<ConventionFlag> flags, ConventionFlag flag) {
    if (flag != null) {
      flags.add(flag);
    }
  }

  /** Grupo D · 16 — commits but none marked as AI-assisted, so the AI metrics read zero. */
  private static ConventionFlag aiFlag(List<RawEvent> commits) {
    if (commits.isEmpty() || commits.stream().anyMatch(RawEvent::ai)) {
      return null;
    }
    return new ConventionFlag(
        "16",
        "warn",
        "Convenção 16 · Assistência de IA",
        "Nenhum commit marca uso de IA",
        "Nenhum dos "
            + commits.size()
            + " commits do período carrega o trailer de IA. Verifique com o dev se ele usa um"
            + " assistente e aplica o trailer padrão (ex.: Co-authored-by: Copilot); sem ele o"
            + " % de commits com IA fica zerado.",
        List.of("ai_share", "ai_adoption"));
  }

  /** Grupo C · 10 — commits but no PR, i.e. code may be reaching main without a pull request. */
  private static ConventionFlag prFlag(List<RawEvent> commits, List<RawEvent> prs) {
    if (commits.isEmpty() || !prs.isEmpty()) {
      return null;
    }
    return new ConventionFlag(
        "10",
        "warn",
        "Convenção 10 · Fluxo de código",
        "Commits sem pull request",
        commits.size()
            + " commits e nenhum PR no período. O código pode estar entrando na main sem pull"
            + " request, ou os PRs deste dev não estão sendo atribuídos à identidade dele.",
        List.of("throughput", "cycle_time", "assertividade"));
  }

  /** Grupo E · 20/21 — commits without work items, or work items with no usable state history. */
  private static ConventionFlag boardFlag(List<RawEvent> commits, List<RawEvent> workItems) {
    if (!commits.isEmpty() && workItems.isEmpty()) {
      return new ConventionFlag(
          "20",
          "warn",
          "Convenção 20 · Boards",
          "Commits sem work item",
          "Atividade de código sem nenhum work item associado. Está commitando sem task"
              + " aberta/tipada? Sem work item não há distribuição por tipo nem WIP para este dev.",
          List.of("distribuição por tipo", "wip"));
    }
    if (!workItems.isEmpty()
        && workItems.stream().noneMatch(w -> w.detail().containsKey("hours"))) {
      return new ConventionFlag(
          "21",
          "info",
          "Convenção 21 · Boards",
          "Board sem transição de estado utilizável",
          "Os "
              + workItems.size()
              + " work items não têm transição de estado aproveitável — o board pode não separar"
              + " 'fazendo' de 'esperando', ou os cards são movidos em lote. WIP e tempo por tipo"
              + " ficam sem dado.",
          List.of("wip", "flow_efficiency", "cycle time de WI"));
    }
    return null;
  }

  /** Grupo C · 13 — the person authored PRs but none carries a registered review. */
  private static ConventionFlag reviewFlag(List<RawEvent> prs, List<RawEvent> reviewsReceived) {
    if (prs.isEmpty() || !reviewsReceived.isEmpty()) {
      return null;
    }
    return new ConventionFlag(
        "13",
        "warn",
        "Convenção 13 · Fluxo de código",
        "PRs sem review registrada",
        "Os PRs deste dev não têm nenhuma review registrada no período. A review pode estar"
            + " acontecendo fora do PR (chat/call), o que zera o tempo de review e a assertividade.",
        List.of("pr_review_time", "% PRs sem review", "assertividade"));
  }

  /**
   * Grupo E · 21 — itens que ficaram em andamento poucos minutos: o card provavelmente foi movido
   * depois do trabalho, não durante. Texto de pergunta e não de acusação: um hotfix real resolvido
   * em dez minutos cai aqui também, e o alerta existe para o gestor conferir a convenção, não para
   * afirmar o que a pessoa fez.
   *
   * <p>O item continua na distribuição — o alerta explica a fatia perto de zero em vez de
   * escondê-la, que é a diferença entre um número estranho e um número enganoso.
   */
  private static ConventionFlag shortItemFlag(WorkDistribution distribution) {
    List<WorkItemEntry> shortItems = distribution.shortItems();
    if (shortItems.isEmpty()) {
      return null;
    }
    String named =
        shortItems.stream()
            .limit(3)
            .map(i -> i.title().isBlank() ? i.id() : i.title())
            .collect(Collectors.joining("; "));
    return new ConventionFlag(
        "21",
        "info",
        "Convenção 21 · Boards",
        shortItems.size() + " item(ns) em andamento por poucos minutos",
        "Itens que ficaram menos de "
            + WorkDistribution.SHORT_ITEM_MINUTES
            + " minutos em andamento: "
            + named
            + ". O card pode ter sido movido depois do trabalho, e aí a hora do item não mede o"
            + " esforço dele. Vale confirmar com o dev se foi isso ou se o trabalho mesmo levou"
            + " esse tempo.",
        List.of("distribuição por tipo", "cycle time de WI", "flow_efficiency"));
  }
}
