package com.engperf.application.metrics;

import java.util.List;

/**
 * The composed Comparativo heatmap for a node: the metric columns (all three groups in catalog
 * order) and one row per compared child with its real value per column. Relative-standing colour is
 * a presentation concern computed by the client.
 */
public record ComparisonHeatmap(String nodeId, List<HeatmapMetric> metrics, List<HeatmapRow> rows) {

  public ComparisonHeatmap {
    metrics = List.copyOf(metrics);
    rows = List.copyOf(rows);
  }
}
