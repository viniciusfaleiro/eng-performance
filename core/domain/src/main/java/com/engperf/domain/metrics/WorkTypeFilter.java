package com.engperf.domain.metrics;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A selection of work types to read by — empty meaning "everything", which is what the product does
 * when nobody chose.
 *
 * <p>The filter restricts the <em>population</em> of a metric, so medians and ratios are recomputed
 * over the selected work rather than composed from per-type values. Which event carries which types
 * is decided at ingestion and read here: a work item carries its own category, and a pull request
 * (with its commits and reviews) carries the categories of the work items it is linked to.
 *
 * <p>An event with no type at all — a pull request linked to nothing — matches no selection and is
 * counted apart, never defaulted into a category. A plausible default here would be a number nobody
 * could doubt on screen.
 */
public record WorkTypeFilter(Set<WorkType> types) {

  /** The detail key of a work item's own category. */
  public static final String TYPE_KEY = "type";

  /** The detail key of the categories a pull request / commit / review inherited from its items. */
  public static final String TYPES_KEY = "work_types";

  private static final WorkTypeFilter NONE = new WorkTypeFilter(EnumSet.noneOf(WorkType.class));

  public WorkTypeFilter {
    types = types == null || types.isEmpty() ? Set.of() : Set.copyOf(types);
  }

  /** No selection: every event contributes, exactly as before the filter existed. */
  public static WorkTypeFilter none() {
    return NONE;
  }

  public static WorkTypeFilter of(WorkType... selected) {
    return new WorkTypeFilter(EnumSet.copyOf(Arrays.asList(selected)));
  }

  public static WorkTypeFilter of(Collection<WorkType> selected) {
    return selected == null || selected.isEmpty()
        ? NONE
        : new WorkTypeFilter(EnumSet.copyOf(selected));
  }

  public boolean isEmpty() {
    return types.isEmpty();
  }

  /** Whether this event belongs to the selected work. An empty selection takes everything. */
  public boolean matches(RawEvent event) {
    if (types.isEmpty()) {
      return true;
    }
    for (WorkType t : typesOf(event)) {
      if (types.contains(t)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether this event carries no work type at all. Under an active selection these are excluded
   * and counted on their own, which is the difference between "no bug work this month" and "we
   * cannot tell, because the pull requests are not linked to the board".
   */
  public static boolean isUntyped(RawEvent event) {
    return typesOf(event).isEmpty();
  }

  /** The categories carried by an event — its own, or the ones inherited from its work items. */
  public static Set<WorkType> typesOf(RawEvent event) {
    if (event == null) {
      return Set.of();
    }
    WorkType own = WorkType.byKey(event.detail().get(TYPE_KEY)).orElse(null);
    if (own != null) {
      return EnumSet.of(own);
    }
    String inherited = event.detail().get(TYPES_KEY);
    if (inherited == null || inherited.isBlank()) {
      return Set.of();
    }
    EnumSet<WorkType> out = EnumSet.noneOf(WorkType.class);
    for (String part : inherited.split(",")) {
      WorkType.byKey(part).ifPresent(out::add);
    }
    return out;
  }

  /** The selected keys in the enum's own order, for echoing a request back. */
  public List<String> keys() {
    return WorkType.all().stream().filter(types::contains).map(WorkType::key).toList();
  }
}
