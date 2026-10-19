package com.engperf.application.port.inbound;

import com.engperf.application.metrics.EntityShare;
import com.engperf.application.metrics.ImpedimentReasons;
import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricDrilldownItem;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricExplanation;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;
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

  /** Every metric for a node, read over everything — no work-type selection. */
  default List<MetricCard> cards(String nodeId, Period period) {
    return cards(nodeId, period, WorkTypeFilter.none());
  }

  /**
   * Every metric for a node, restricted to a work-type selection. A metric that cannot be read by
   * work type (deploy-based) is computed over its whole population and says so on the card.
   */
  List<MetricCard> cards(String nodeId, Period period, WorkTypeFilter workTypes);

  default MetricSeries series(String metricKey, String nodeId, Period period) {
    return series(metricKey, nodeId, period, WorkTypeFilter.none());
  }

  MetricSeries series(String metricKey, String nodeId, Period period, WorkTypeFilter workTypes);

  /**
   * Series for a metric computed over only the AI-assisted ({@code aiAssisted=true}) or non-AI
   * cohort of the node's population — the two cohorts partition the population by the event AI
   * flag.
   */
  default MetricSeries cohortSeries(
      String metricKey, String nodeId, Period period, boolean aiAssisted) {
    return cohortSeries(metricKey, nodeId, period, aiAssisted, WorkTypeFilter.none());
  }

  /** As {@link #cohortSeries}, with the cohort narrowed by a work-type selection. */
  MetricSeries cohortSeries(
      String metricKey, String nodeId, Period period, boolean aiAssisted, WorkTypeFilter workTypes);

  /**
   * The raw events considered for {@code metricKey} at {@code nodeId} in {@code period} — the same
   * interval the card for that period shows.
   */
  default List<MetricDrilldownItem> items(String metricKey, String nodeId, Period period) {
    return items(metricKey, nodeId, period, WorkTypeFilter.none());
  }

  /** As {@link #items}, under the same work-type selection as the card that opened the list. */
  List<MetricDrilldownItem> items(
      String metricKey, String nodeId, Period period, WorkTypeFilter workTypes);

  /**
   * Per-person breakdown of a metric that counts distinct people, ordered from the lowest share to
   * the highest. Listing the events would explain the wrong thing: the metric's unit is the person.
   *
   * @throws IllegalArgumentException if the metric does not count distinct entities
   */
  default List<EntityShare> entityShares(String metricKey, String nodeId, Period period) {
    return entityShares(metricKey, nodeId, period, WorkTypeFilter.none());
  }

  /** As {@link #entityShares}, under the active work-type selection. */
  List<EntityShare> entityShares(
      String metricKey, String nodeId, Period period, WorkTypeFilter workTypes);

  /**
   * What the waiting time of {@code nodeId}'s population in {@code period} was spent being blocked
   * by, over the very items the waiting phase was computed from. The slices compose that
   * population's waiting hours — they do not decompose the waiting card, which is a median.
   */
  ImpedimentReasons impediments(String nodeId, Period period, WorkTypeFilter workTypes);
}
