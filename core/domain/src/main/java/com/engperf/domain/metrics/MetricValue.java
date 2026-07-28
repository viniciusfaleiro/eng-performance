package com.engperf.domain.metrics;

/**
 * A metric's value for a node in a bucket, with its change vs. the comparison period and the
 * resolved polarity. {@code changePct} is the signed raw percentage change (null when there is no
 * comparable previous value); {@code sentiment} tells whether that change is good or bad.
 */
public record MetricValue(double value, Double previous, Double changePct, Sentiment sentiment) {

  public static MetricValue of(double value, Double previous, Direction direction) {
    Double changePct = null;
    if (previous != null && previous != 0.0) {
      changePct = (value - previous) / Math.abs(previous) * 100.0;
    }
    return new MetricValue(value, previous, changePct, Sentiment.of(value, previous, direction));
  }
}
