package com.engperf.application.metrics;

import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import java.util.List;

/** A metric's series over the requested buckets, plus its attribution coverage for the window. */
public record MetricSeries(
    MetricDefinition definition, List<SeriesPoint> points, Coverage coverage) {

  public MetricSeries {
    points = List.copyOf(points);
  }
}
