package com.engperf.application.metrics;

import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which events the reader's selections leave in.
 *
 * <p>Split out of {@link MetricsEngine} along a real seam, as the item list was before it:
 * everything here answers "which population does this reading describe?", while what stayed answers
 * "what is the number". Each selection also decides for itself whether it reaches a given metric —
 * a filter that silently does nothing is worse than one that says it does not apply.
 */
final class MetricSelections {

  private MetricSelections() {}

  /**
   * Whether this selection reaches this metric. A deploy metric never filters (see {@link
   * MetricDefinition#filterableByWorkType()}), and an empty selection filters nothing at all.
   */
  public static boolean filters(MetricDefinition def, WorkTypeFilter workTypes) {
    return !workTypes.isEmpty() && def.filterableByWorkType();
  }

  /**
   * The cohort predicate narrowed by the work-type selection, when the selection applies. The two
   * are composed into one population so every statistic — median included — is recomputed over the
   * selected work instead of being assembled from per-type values.
   */
  public static Predicate<RawEvent> restrict(
      Predicate<RawEvent> population, MetricDefinition def, WorkTypeFilter workTypes) {
    return filters(def, workTypes) ? population.and(workTypes::matches) : population;
  }

  /**
   * Whether a container selection reaches this metric. Only a work-item metric has a container side
   * to be on: a deploy belongs to a pipeline and a pull request to a repository, and neither groups
   * work items. Saying so is what keeps the selection from looking silently ignored.
   */
  public static boolean filters(MetricDefinition def, ContainerFilter containers) {
    return !containers.isEmpty() && def.filterableByContainer();
  }

  /** The population narrowed by every selection it carries, each only where it applies. */
  public static Predicate<RawEvent> restrict(
      Predicate<RawEvent> population, MetricDefinition def, MetricPopulation requested) {
    Predicate<RawEvent> byType = restrict(population, def, requested.workTypes());
    ContainerFilter containers = requested.containers();
    Set<String> parentIds = requested.parentIds();
    return filters(def, containers) ? byType.and(e -> containers.matches(e, parentIds)) : byType;
  }
}
