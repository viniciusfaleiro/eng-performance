package com.engperf.application.metrics;

import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which events a metric is computed over: the cohort a metric declares for itself (completed work
 * items, the AI cohort) and the reader's selections — work type and container side.
 *
 * <p>The two travel together because they answer the same question — "which population?" — and
 * because the engine composes them into one predicate. Passing them apart also pushed the engine's
 * signatures past what anyone can read at a call site.
 */
public record MetricPopulation(
    Predicate<RawEvent> cohort,
    WorkTypeFilter workTypes,
    ContainerFilter containers,
    Set<String> parentIds) {

  public MetricPopulation {
    Objects.requireNonNull(cohort, "cohort must not be null");
    workTypes = workTypes == null ? WorkTypeFilter.none() : workTypes;
    containers = containers == null ? ContainerFilter.none() : containers;
    // Os ids dos pais vêm do corpus inteiro, não do período: é o que mantém a classificação de um
    // item estável quando o leitor muda a janela.
    parentIds = parentIds == null ? Set.of() : Set.copyOf(parentIds);
  }

  public MetricPopulation(Predicate<RawEvent> cohort, WorkTypeFilter workTypes) {
    this(cohort, workTypes, ContainerFilter.none(), Set.of());
  }

  /** Every event the metric's own cohort allows, with no selection at all. */
  public static MetricPopulation of(Predicate<RawEvent> cohort) {
    return new MetricPopulation(cohort, WorkTypeFilter.none());
  }
}
