package com.engperf.application.port.inbound;

import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import java.util.List;

/**
 * Inbound port for reading metrics. Node-scope enforcement (403 outside scope, coaching-only) lives
 * in the web adapter using the S2 access scope; this port computes values for an already-authorized
 * node.
 */
public interface MetricsQueryUseCase {

  List<MetricDefinition> catalog();

  List<MetricCard> cards(String nodeId, Frequency frequency);

  MetricSeries series(String metricKey, String nodeId, Frequency frequency);
}
