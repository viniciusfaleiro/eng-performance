package com.engperf.domain.metrics;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The interval a number describes: one bucket of a {@link Frequency}, identified by the date it
 * starts on.
 *
 * <p>Built from *any* date inside it, so a caller never has to know where a week begins or how to
 * round a month — {@code of(WEEKLY, 15 July)} and {@code of(WEEKLY, 17 July)} are the same period.
 * That rounding used to live implicitly in every call site that derived "today" from the clock.
 *
 * <p>Carries no clock of its own. Whether a period is in progress or still in the future is a
 * question about some reference day, and the caller is the one that knows which day that is —
 * production uses the real clock, the demo environment pins it.
 */
public record Period(Frequency frequency, LocalDate start) {

  public Period {
    Objects.requireNonNull(frequency, "frequency must not be null");
    Objects.requireNonNull(start, "start must not be null");
    if (!start.equals(frequency.bucketStart(start))) {
      throw new IllegalArgumentException(
          "period start must be the first day of its bucket: " + start + " (" + frequency + ")");
    }
  }

  /** The period containing {@code anyDateInside}, at this frequency. */
  public static Period of(Frequency frequency, LocalDate anyDateInside) {
    Objects.requireNonNull(frequency, "frequency must not be null");
    Objects.requireNonNull(anyDateInside, "date must not be null");
    return new Period(frequency, frequency.bucketStart(anyDateInside));
  }

  /** The period containing {@code today} — what the system shows when nobody chose one. */
  public static Period current(Frequency frequency, LocalDate today) {
    return of(frequency, today);
  }

  /** The day after the last one in this period. */
  public LocalDate end() {
    return frequency.nextBucketStart(start);
  }

  public Period previous() {
    return new Period(frequency, frequency.previousBucketStart(start));
  }

  public Period next() {
    return new Period(frequency, frequency.nextBucketStart(start));
  }

  public boolean contains(LocalDate date) {
    return !date.isBefore(start) && date.isBefore(end());
  }

  /**
   * Whether this period is the one still running as of {@code today}. This is what decides between
   * comparing an elapsed slice and comparing in full: a month in progress must be measured against
   * the same slice of the month before, while a month that already ended must be measured whole.
   */
  public boolean inProgress(LocalDate today) {
    return contains(today);
  }

  /** The same period at another frequency, anchored on the day this one starts. */
  public Period at(Frequency other) {
    return of(other, start);
  }

  /**
   * Fails for a period that has not begun. Deliberately not the same as a period with no events: a
   * future period has no correct answer, while an empty past period answers zero — and hiding a
   * month in which nothing was delivered would hide exactly the month worth looking at.
   */
  public void requireNotFuture(LocalDate today) {
    if (start.isAfter(today)) {
      throw new IllegalArgumentException(
          "período ainda não começou: " + start + " (hoje é " + today + ")");
    }
  }
}
