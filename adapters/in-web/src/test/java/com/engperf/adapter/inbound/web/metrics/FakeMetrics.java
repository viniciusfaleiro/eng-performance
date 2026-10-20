package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.EntityShare;
import com.engperf.application.metrics.ImpedimentReasons;
import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricDrilldownItem;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.application.metrics.SeriesPoint;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.time.Instant;
import java.util.List;

/**
 * Catálogo de uma métrica só, com as seleções que o controller recebeu guardadas para conferência.
 */
final class FakeMetrics implements MetricsQueryUseCase {

  static final MetricDefinition DEF =
      new MetricDefinition(
          "throughput",
          "Throughput",
          "fluxo",
          EventType.PR,
          AttributionScope.PERSON,
          Aggregation.SUM,
          "PRs",
          Direction.HIGHER_BETTER);

  @Override
  public ImpedimentReasons impediments(
      String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers) {
    return ImpedimentReasons.none();
  }

  /** The last work-type selection this fake was asked for — what the API test checks. */
  static WorkTypeFilter lastFilter;

  @Override
  public List<MetricDefinition> catalog() {
    return List.of(DEF);
  }

  @Override
  public java.util.Map<String, com.engperf.domain.metrics.MetricExplanation> viewExplanations() {
    return java.util.Map.of(
        "trend", new com.engperf.domain.metrics.MetricExplanation("r", "s", "i", "e", "p", "x"));
  }

  @Override
  public String attributionNote() {
    return "as-of-event";
  }

  @Override
  public List<MetricCard> cards(
      String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers) {
    lastFilter = workTypes;
    MetricValue v = MetricValue.of(period.frequency().ordinal(), null, DEF.direction());
    return List.of(new MetricCard(DEF, v, new Coverage(9, 10)));
  }

  @Override
  public MetricSeries series(
      String metricKey,
      String nodeId,
      Period period,
      WorkTypeFilter workTypes,
      ContainerFilter containers) {
    lastFilter = workTypes;
    MetricValue v = MetricValue.of(period.frequency().ordinal(), null, DEF.direction());
    return new MetricSeries(DEF, List.of(new SeriesPoint("2026-06-01", v)), new Coverage(9, 10));
  }

  @Override
  public MetricSeries cohortSeries(
      String metricKey,
      String nodeId,
      Period period,
      boolean aiAssisted,
      WorkTypeFilter workTypes) {
    lastFilter = workTypes;
    MetricValue v = MetricValue.of(period.frequency().ordinal(), null, DEF.direction());
    return new MetricSeries(DEF, List.of(new SeriesPoint("2026-06-01", v)), new Coverage(9, 10));
  }

  @Override
  public List<EntityShare> entityShares(
      String metricKey,
      String nodeId,
      Period period,
      WorkTypeFilter workTypes,
      ContainerFilter containers) {
    lastFilter = workTypes;
    return List.of(
        new EntityShare("p:ana", "Ana", 0, 3), new EntityShare("p:bruno", "Bruno", 2, 2));
  }

  @Override
  public List<MetricDrilldownItem> items(
      String metricKey,
      String nodeId,
      Period period,
      WorkTypeFilter workTypes,
      ContainerFilter containers) {
    lastFilter = workTypes;
    return List.of(
        new MetricDrilldownItem(
            "pr:1",
            EventType.PR,
            "https://ado/pr/1",
            // echoes the resolved interval so the test can assert what the engine was handed
            "period=" + period.start() + ".." + period.end(),
            nodeId,
            Instant.parse("2026-06-10T10:00:00Z"),
            1.0,
            true,
            null));
  }
}
