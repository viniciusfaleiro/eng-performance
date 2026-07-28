package com.engperf.domain.metrics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Bucketing granularity. Weeks are ISO (Monday start); all dates are UTC calendar dates. */
public enum Frequency {
  DAILY,
  WEEKLY,
  MONTHLY;

  /** The start date of the bucket containing {@code date}. */
  public LocalDate bucketStart(LocalDate date) {
    return switch (this) {
      case DAILY -> date;
      case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTHLY -> date.withDayOfMonth(1);
    };
  }

  /** The start date of the bucket immediately after the one starting at {@code start}. */
  public LocalDate nextBucketStart(LocalDate start) {
    return switch (this) {
      case DAILY -> start.plusDays(1);
      case WEEKLY -> start.plusWeeks(1);
      case MONTHLY -> start.plusMonths(1);
    };
  }

  /** The start date of the bucket immediately before the one starting at {@code start}. */
  public LocalDate previousBucketStart(LocalDate start) {
    return switch (this) {
      case DAILY -> start.minusDays(1);
      case WEEKLY -> start.minusWeeks(1);
      case MONTHLY -> start.minusMonths(1);
    };
  }

  /** Returns {@code n} consecutive buckets ending with the one containing {@code reference}. */
  public List<Bucket> lastBuckets(LocalDate reference, int n) {
    List<LocalDate> starts = new ArrayList<>();
    LocalDate cursor = bucketStart(reference);
    for (int i = 0; i < n; i++) {
      starts.add(cursor);
      cursor = previousBucketStart(cursor);
    }
    List<Bucket> buckets = new ArrayList<>();
    for (int i = starts.size() - 1; i >= 0; i--) {
      LocalDate s = starts.get(i);
      buckets.add(new Bucket(s, nextBucketStart(s)));
    }
    return buckets;
  }

  /** Days elapsed into the bucket starting at {@code start}, inclusive, as of {@code reference}. */
  public int elapsedDays(LocalDate start, LocalDate reference) {
    LocalDate end = nextBucketStart(start);
    if (!reference.isBefore(end)) {
      return (int) (end.toEpochDay() - start.toEpochDay());
    }
    return (int) (reference.toEpochDay() - start.toEpochDay()) + 1;
  }
}
