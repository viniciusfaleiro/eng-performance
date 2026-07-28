package com.engperf.domain.metrics;

/** Attribution completeness: how many events in scope resolved to a person/team vs. the total. */
public record Coverage(long attributed, long total) {

  public Coverage {
    if (attributed < 0 || total < 0 || attributed > total) {
      throw new IllegalArgumentException("invalid coverage counts");
    }
  }

  public double percent() {
    return total == 0 ? 100.0 : (double) attributed / total * 100.0;
  }

  public long unattributed() {
    return total - attributed;
  }
}
