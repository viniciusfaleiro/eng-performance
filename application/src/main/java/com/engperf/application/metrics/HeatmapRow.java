package com.engperf.application.metrics;

import java.util.List;

/**
 * One row of the comparison heatmap: a child node (team, vertical or person) and its real value for
 * each metric column, in the heatmap's column order. {@code rowType} is the structural level of the
 * compared children ("Time"/"Vertical"/"Pessoa").
 */
public record HeatmapRow(String nodeId, String label, String rowType, List<Double> values) {

  public HeatmapRow {
    values = List.copyOf(values);
  }
}
