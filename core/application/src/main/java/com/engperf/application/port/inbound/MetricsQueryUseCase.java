package com.engperf.application.port.inbound;

import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricDrilldownItem;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricExplanation;
import com.engperf.domain.metrics.Period;
import java.util.List;
import java.util.Map;

/**
 * Inbound port for reading metrics. Node-scope enforcement (403 outside scope, coaching-only) lives
 * in the web adapter using the S2 access scope; this port computes values for an already-authorized
 * node.
 */
public interface MetricsQueryUseCase {

  List<MetricDefinition> catalog();

  /** Explanations for charts and panels that are not a single metric, keyed by view. */
  Map<String, MetricExplanation> viewExplanations();

  /** How attribution works — the same sentence for every metric, so it is stated once. */
  String attributionNote();

  List<MetricCard> cards(String nodeId, Period period);

  MetricSeries series(String metricKey, String nodeId, Period period);

  /**
   * Series for a metric computed over only the AI-assisted ({@code aiAssisted=true}) or non-AI
   * cohort of the node's population — the two cohorts partition the population by the event AI
   * flag.
   */
  MetricSeries cohortSeries(String metricKey, String nodeId, Period period, boolean aiAssisted);

  /**
   * The raw events considered for {@code metricKey} at {@code nodeId} in {@code period} — the same
   * interval the card for that period shows.
   */
  List<MetricDrilldownItem> items(String metricKey, String nodeId, Period period);
}
