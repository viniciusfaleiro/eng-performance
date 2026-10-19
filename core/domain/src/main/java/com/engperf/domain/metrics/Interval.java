package com.engperf.domain.metrics;

/**
 * Half-open {@code [start, end)} in epoch milliseconds.
 *
 * <p>Top-level and not nested in the one type that first needed it, because two unrelated readings
 * now describe a work item as stretches of time — the intervals it was in progress and the
 * intervals it was impeded — and they are compared against each other. Naming the second one after
 * the first would make every line that subtracts one from the other read as if it were about
 * progress.
 */
public record Interval(long start, long end) {

  public Interval {
    if (start >= end) {
      throw new IllegalArgumentException("intervalo vazio ou invertido: " + start + ".." + end);
    }
  }

  public double hours() {
    return (end - start) / 3_600_000.0;
  }
}
