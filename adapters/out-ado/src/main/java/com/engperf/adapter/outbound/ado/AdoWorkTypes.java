package com.engperf.adapter.outbound.ado;

import com.engperf.domain.metrics.WorkType;
import com.engperf.domain.metrics.WorkTypeFilter;
import com.engperf.domain.metrics.WorkTypeMapping;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How an Azure DevOps payload becomes a work-type category, and how a category reaches an event's
 * detail.
 *
 * <p>Separate from {@link AdoMapper} because it is the one place that knows both the board's
 * vocabulary (a task is classified by its parent) and ours (the five categories of {@link
 * WorkTypeMapping}). The table itself lives in the domain; this only applies it.
 */
final class AdoWorkTypes {

  private AdoWorkTypes() {}

  /**
   * The work type of an item, given its own Azure DevOps type and that of its parent.
   *
   * <p>A task carries no nature of its own — "write the test" is feature work under a user story
   * and technical debt under a debt item — so it takes the parent's. Only one level is climbed: a
   * task whose parent is also a task falls back, because following the chain would make the number
   * of requests a function of how deep each team nests its board.
   */
  static WorkType category(String adoType, String parentAdoType) {
    if (!isTask(adoType)) {
      return WorkTypeMapping.of(adoType);
    }
    if (parentAdoType == null || parentAdoType.isBlank() || isTask(parentAdoType)) {
      return WorkType.FALLBACK;
    }
    return WorkTypeMapping.of(parentAdoType);
  }

  /**
   * Whether the declared table recognises what classified this item — {@code false} means the
   * category is the fallback and nobody declared it. A task is judged by its parent, which is what
   * decides it; a task with no usable parent is unmapped by the same reasoning.
   */
  static boolean isMapped(String adoType, String parentAdoType) {
    if (!isTask(adoType)) {
      return WorkTypeMapping.isMapped(adoType);
    }
    return parentAdoType != null && WorkTypeMapping.isMapped(parentAdoType);
  }

  /** Whether the raw type is recognised on its own — used by the unmapped-type report. */
  static boolean isMapped(String adoType) {
    return isTask(adoType) || WorkTypeMapping.isMapped(adoType);
  }

  private static boolean isTask(String adoType) {
    return adoType != null && "task".equals(adoType.toLowerCase(Locale.ROOT));
  }

  /**
   * Writes the inherited categories on an event, in the enum's order so the same set always reads
   * the same. Nothing is written when there is nothing: an absent key is how "this pull request is
   * linked to no work item" stays distinguishable from a category.
   */
  static void apply(Map<String, String> detail, Set<WorkType> workTypes) {
    if (workTypes == null || workTypes.isEmpty()) {
      return;
    }
    String joined =
        WorkType.all().stream()
            .filter(workTypes::contains)
            .map(WorkType::key)
            .collect(Collectors.joining(","));
    detail.put(WorkTypeFilter.TYPES_KEY, joined);
  }
}
