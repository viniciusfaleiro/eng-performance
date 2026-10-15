package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;

/**
 * One Fluxo card: the metric, its value for the period, its coverage, and what the work-type
 * selection did to it — see {@link MetricCard} for what {@code filtered} and {@code
 * untypedExcluded} mean.
 */
public record FlowCard(
    MetricDefinition definition,
    MetricValue value,
    Coverage coverage,
    boolean filtered,
    long untypedExcluded) {

  public FlowCard(MetricDefinition definition, MetricValue value, Coverage coverage) {
    this(definition, value, coverage, false, 0L);
  }

  /** The card as the engine computed it, carrying the filter outcome along. */
  public static FlowCard of(MetricDefinition definition, MetricCard card) {
    return new FlowCard(
        definition, card.current(), card.coverage(), card.filtered(), card.untypedExcluded());
  }
}
