package com.engperf.application.metrics;

import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Which events a metric is computed over: the cohort a metric declares for itself (completed work
 * items, the AI cohort) and the reader's work-type selection.
 *
 * <p>The two travel together because they answer the same question — "which population?" — and
 * because the engine composes them into one predicate. Passing them apart also pushed the engine's
 * signatures past what anyone can read at a call site.
 */
public record MetricPopulation(Predicate<RawEvent> cohort, WorkTypeFilter workTypes) {

  public MetricPopulation {
    Objects.requireNonNull(cohort, "cohort must not be null");
    workTypes = workTypes == null ? WorkTypeFilter.none() : workTypes;
  }

  /** Every event the metric's own cohort allows, with no work-type selection. */
  public static MetricPopulation of(Predicate<RawEvent> cohort) {
    return new MetricPopulation(cohort, WorkTypeFilter.none());
  }
}
