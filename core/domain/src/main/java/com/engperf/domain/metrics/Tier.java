package com.engperf.domain.metrics;

/**
 * DORA benchmark classification of a metric value. Only DORA metrics (those with {@link TierBands})
 * classify; others have no tier. The bands are direction-aware.
 */
public enum Tier {
  ELITE,
  ALTO,
  MEDIO,
  BAIXO;

  /**
   * Classifies {@code value} against {@code bands}, respecting {@code direction}. Higher-is-better
   * uses {@code >=} boundaries; lower-is-better uses {@code <=} boundaries.
   */
  public static Tier of(double value, TierBands bands, Direction direction) {
    if (direction == Direction.HIGHER_BETTER) {
      if (value >= bands.elite()) {
        return ELITE;
      }
      if (value >= bands.alto()) {
        return ALTO;
      }
      if (value >= bands.medio()) {
        return MEDIO;
      }
      return BAIXO;
    }
    if (value <= bands.elite()) {
      return ELITE;
    }
    if (value <= bands.alto()) {
      return ALTO;
    }
    if (value <= bands.medio()) {
      return MEDIO;
    }
    return BAIXO;
  }
}
