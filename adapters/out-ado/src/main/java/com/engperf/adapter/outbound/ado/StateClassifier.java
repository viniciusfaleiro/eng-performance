package com.engperf.adapter.outbound.ado;

import java.util.List;
import java.util.Locale;

/**
 * Classifies a work-item state into a flow {@link Segment}, by ADO state category when available or
 * a name heuristic otherwise. Keyword lists are centralised here (the configurable seam), never
 * hardcoded per process template.
 */
final class StateClassifier {

  private StateClassifier() {}

  private static final List<String> TERMINAL_HINTS =
      List.of("done", "closed", "resolved", "completed", "removed", "cancel", "reject");
  private static final List<String> WAIT_HINTS =
      List.of("blocked", "hold", "waiting", "ready", "backlog", "to do", "todo", "new", "proposed");
  private static final List<String> REVIEW_HINTS = List.of("review", "testing", "qa", "verify");

  /** Maps an ADO state category (+ name for the InProgress review/active split) to a segment. */
  static Segment fromCategory(String category, String name) {
    return switch (category.toLowerCase(Locale.ROOT)) {
      case "completed", "resolved", "removed" -> Segment.DONE;
      case "proposed" -> Segment.WAITING;
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
