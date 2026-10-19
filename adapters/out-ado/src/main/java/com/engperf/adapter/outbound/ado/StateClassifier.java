package com.engperf.adapter.outbound.ado;

import java.util.List;
import java.util.Locale;

/**
 * Classifies a work-item state into a flow {@link Segment}, by ADO state category when available or
 * a name heuristic otherwise. Keyword lists are centralised here (the configurable seam), never
 * hardcoded per process template.
 *
 * <p>The queue list refines the waiting category rather than competing with it: a state it matches
 * still counts as waiting in every phase figure, and only tells the ingestion that the team had
 * already taken the item on by then.
 */
final class StateClassifier {

  private StateClassifier() {}

  private static final List<String> TERMINAL_HINTS =
      List.of("done", "closed", "resolved", "completed", "removed", "cancel", "reject");
  private static final List<String> WAIT_HINTS =
      List.of("blocked", "hold", "waiting", "backlog", "to do", "todo", "new", "proposed");
  // Fila depois do compromisso: o item já é do time e espera a próxima mão, não uma decisão sobre
  // pegá-lo. Checada antes da espera, porque "ready" estava naquela lista e é o caso mais comum.
  private static final List<String> QUEUE_HINTS = List.of("ready", "pronto", "aguardando deploy");
  private static final List<String> REVIEW_HINTS = List.of("review", "testing", "qa", "verify");

  /** Maps an ADO state category (+ name for the InProgress review/active split) to a segment. */
  static Segment fromCategory(String category, String name) {
    return switch (category.toLowerCase(Locale.ROOT)) {
      case "completed", "resolved", "removed" -> Segment.DONE;
      case "proposed" -> matches(name, QUEUE_HINTS) ? Segment.QUEUE : Segment.WAITING;
      case "inprogress" -> matches(name, REVIEW_HINTS) ? Segment.REVIEW : Segment.ACTIVE;
      default -> byName(name);
    };
  }

  /** Name heuristic used when the ADO category is unavailable. */
  static Segment byName(String state) {
    if (matches(state, TERMINAL_HINTS)) {
      return Segment.DONE;
    }
    if (matches(state, REVIEW_HINTS)) {
      return Segment.REVIEW;
    }
    if (matches(state, QUEUE_HINTS)) {
      return Segment.QUEUE;
    }
    if (matches(state, WAIT_HINTS)) {
      return Segment.WAITING;
    }
    return Segment.ACTIVE;
  }

  private static boolean matches(String state, List<String> hints) {
    String s = state.toLowerCase(Locale.ROOT);
    return hints.stream().anyMatch(s::contains);
  }
}
