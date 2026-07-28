package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;

/** An IA metric's current value for a node: value/evolution and coverage (no DORA tier). */
public record AiCard(MetricDefinition definition, MetricValue value, Coverage coverage) {}
