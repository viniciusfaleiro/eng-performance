package com.engperf.domain.metrics;

import java.util.Optional;

/**
 * Classifies a metric value into a benchmark {@link Tier}. Only metrics carrying {@link TierBands}
 * classify. Count-style metrics (aggregation {@code SUM}, e.g. Deployment Frequency) are normalized
 * to a per-day rate first, so the tier is independent of the selected frequency bucket.
 */
public final class Benchmark {

  private Benchmark() {}

  public static Optional<Tier> classify(MetricDefinition def, double value, int bucketDays) {
    return def.tierBands()
        .map(bands -> Tier.of(normalize(def, value, bucketDays), bands, def.direction()));
  }

  static double normalize(MetricDefinition def, double value, int bucketDays) {
    if (def.aggregation() == Aggregation.SUM && bucketDays > 0) {
      return value / bucketDays;
    }
    return value;
  }
}
