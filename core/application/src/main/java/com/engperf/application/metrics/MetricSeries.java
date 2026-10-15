package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import java.util.List;

/**
 * A metric's series over the requested buckets, plus its attribution coverage for the window and
 * what the work-type selection did to it — see {@link MetricCard} for what the two last components
 * mean.
 */
public record MetricSeries(
    MetricDefinition definition,
    List<SeriesPoint> points,
    Coverage coverage,
    boolean filtered,
    long untypedExcluded) {

  public MetricSeries {
    points = List.copyOf(points);
  }

  /** An unfiltered reading — every caller that does not pass a work-type selection. */
  public MetricSeries(MetricDefinition definition, List<SeriesPoint> points, Coverage coverage) {
    this(definition, points, coverage, false, 0L);
  }
}
