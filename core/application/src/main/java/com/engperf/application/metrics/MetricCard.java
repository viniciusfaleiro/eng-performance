package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;

/**
 * A metric's current value (last bucket) with its evolution and coverage, for a node's card grid.
 *
 * <p>{@code filtered} says whether the active work-type selection reached this metric — a
 * deploy-based metric is read over everything, and the card has to be able to say so instead of
 * looking like it answered a question it cannot answer. {@code untypedExcluded} is how many of the
 * node's events were left out for carrying no work type: it is what separates "no bug work in this
 * period" from "we cannot tell, because the pull requests are not linked to the board".
 */
public record MetricCard(
    MetricDefinition definition,
    MetricValue current,
    Coverage coverage,
    boolean filtered,
    long untypedExcluded) {

  /** An unfiltered reading — every caller that does not pass a work-type selection. */
  public MetricCard(MetricDefinition definition, MetricValue current, Coverage coverage) {
    this(definition, current, coverage, false, 0L);
  }
}
