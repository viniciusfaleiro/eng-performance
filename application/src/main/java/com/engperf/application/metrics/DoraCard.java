package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import com.engperf.domain.metrics.Tier;
import java.util.Optional;

/** A DORA metric's current value for a node: value/evolution, its benchmark tier, and coverage. */
public record DoraCard(
    MetricDefinition definition, MetricValue value, Tier tier, Coverage coverage) {

  public Optional<Tier> tierOptional() {
    return Optional.ofNullable(tier);
  }
}
