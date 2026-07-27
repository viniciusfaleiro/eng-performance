package com.engperf.domain.metrics;

import java.time.LocalDate;
import java.util.Objects;

/** A half-open date range [start, endExclusive) identifying one bucket of a series. */
public record Bucket(LocalDate start, LocalDate endExclusive) {

  public Bucket {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(endExclusive, "endExclusive must not be null");
    if (!endExclusive.isAfter(start)) {
      throw new IllegalArgumentException("bucket end must be after start");
    }
  }

  public boolean contains(LocalDate date) {
    return !date.isBefore(start) && date.isBefore(endExclusive);
  }
}
