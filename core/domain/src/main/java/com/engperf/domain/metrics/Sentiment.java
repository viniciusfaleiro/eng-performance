package com.engperf.domain.metrics;

/**
 * The resolved polarity of a metric's change: green/red by good/bad, not by up/down. A lower-is-
 * better metric that fell is {@code GOOD}; a higher-is-better metric that fell is {@code BAD}.
 */
public enum Sentiment {
  GOOD,
  BAD,
  NEUTRAL;

  /** Resolves the sentiment of moving from {@code previous} to {@code current} for a direction. */
  public static Sentiment of(Double current, Double previous, Direction direction) {
    if (current == null || previous == null || current.doubleValue() == previous.doubleValue()) {
      return NEUTRAL;
    }
    boolean increased = current > previous;
    boolean improved = direction == Direction.HIGHER_BETTER ? increased : !increased;
    return improved ? GOOD : BAD;
  }
}
