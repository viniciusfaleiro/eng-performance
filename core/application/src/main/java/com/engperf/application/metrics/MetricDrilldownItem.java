package com.engperf.application.metrics;

import com.engperf.domain.metrics.EventType;
import java.time.Instant;

/**
 * One raw event considered by a metric's aggregation for a node in a period — the troubleshooting
 * unit: a link back to the Azure DevOps record, the entity it was attributed to, and the measure it
 * contributed. {@code counted} mirrors exactly whether {@link MetricsEngine#items} included it in
 * the aggregated value; when {@code false}, {@code excludedReason} says why (e.g. no measure, or
 * superseded by a more recent event of the same entity in a {@code SNAPSHOT} metric).
 */
public record MetricDrilldownItem(
    String eventId,
    EventType eventType,
    String url,
    String label,
    String entity,
    Instant occurredAt,
    double measure,
    boolean counted,
    String excludedReason) {}
