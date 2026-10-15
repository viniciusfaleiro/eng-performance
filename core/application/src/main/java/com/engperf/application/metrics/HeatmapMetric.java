package com.engperf.application.metrics;

/**
 * One heatmap column. {@code filterable} says whether the active work-type selection reached it —
 * the deploy-based DORA columns never filter, and a column that ignores the filter has to be
 * marked, or the reader compares two populations believing they are one.
 */
public record HeatmapMetric(String key, String label, String unit, boolean filterable) {

  public HeatmapMetric(String key, String label, String unit) {
    this(key, label, unit, true);
  }
}
