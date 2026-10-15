package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;

/**
 * One IA card: the metric, its value for the period, its coverage, and what the work-type selection
 * did to it — see {@link MetricCard} for what {@code filtered} and {@code untypedExcluded} mean. On
 * this dashboard the excluded count matters more than anywhere else: the AI signal lives on commits
 * and pull requests, so a team that does not link its pull requests to the board has a filtered
 * reading built on very little.
 */
public record AiCard(
    MetricDefinition definition,
    MetricValue value,
    Coverage coverage,
    boolean filtered,
    long untypedExcluded) {

  public AiCard(MetricDefinition definition, MetricValue value, Coverage coverage) {
    this(definition, value, coverage, false, 0L);
  }

  public static AiCard of(MetricDefinition definition, MetricCard card) {
    return new AiCard(
        definition, card.current(), card.coverage(), card.filtered(), card.untypedExcluded());
  }
}
