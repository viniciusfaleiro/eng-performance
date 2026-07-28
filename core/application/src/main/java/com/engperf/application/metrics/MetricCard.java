package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;

/**
 * A metric's current value (last bucket) with its evolution and coverage, for a node's card grid.
 */
public record MetricCard(MetricDefinition definition, MetricValue current, Coverage coverage) {}
