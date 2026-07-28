package com.engperf.domain.metrics;

import java.util.Arrays;

/**
 * Pure roll-up math. {@code median} and {@code ratio} operate on the whole population handed in —
 * the engine collects every event under the queried node and calls these directly, so a node's
 * value is never composed from children's medians/ratios.
 */
public final class Aggregations {

  private Aggregations() {}

  public static double sum(double[] values) {
    double s = 0.0;
    for (double v : values) {
      s += v;
    }
    return s;
  }

  public static double median(double[] values) {
    if (values.length == 0) {
      return 0.0;
    }
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    int mid = sorted.length / 2;
    return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
  }

  /** Volume-weighted ratio: sum of numerators over sum of denominators (0 when no denominator). */
  public static double ratio(double numeratorSum, double denominatorSum) {
    return denominatorSum == 0.0 ? 0.0 : numeratorSum / denominatorSum;
  }
}
