package com.engperf.domain.metrics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * The interval a number describes: a start, an exclusive end, and the {@link Frequency} that gives
 * the interval its granularity.
 *
 * <p>A calendar bucket — a day, an ISO week, a month — is a period whose edges happen to coincide
 * with the frequency's bucket; {@link #isBucket()} answers that. Keeping one type for both is
 * deliberate: two types would force every service to ask which one it received, and it would only
 * be a matter of time until one of them handled a case badly. What differs between the two is asked
 * where it matters, not carried in the type.
 *
 * <p>Carries no clock of its own. Whether a period is in progress or still in the future is a
 * question about some reference day, and the caller is the one that knows which day that is —
 * production uses the real clock, the demo environment pins it.
 */
public record Period(Frequency frequency, LocalDate start, LocalDate end) {

  public Period {
    Objects.requireNonNull(frequency, "frequency must not be null");
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("período termina antes de começar: " + start + ".." + end);
    }
  }

  /** The calendar bucket containing {@code anyDateInside}, at this frequency. */
  public static Period of(Frequency frequency, LocalDate anyDateInside) {
    Objects.requireNonNull(frequency, "frequency must not be null");
    Objects.requireNonNull(anyDateInside, "date must not be null");
    LocalDate bucketStart = frequency.bucketStart(anyDateInside);
    return new Period(frequency, bucketStart, frequency.nextBucketStart(bucketStart));
  }

  /** The calendar bucket containing {@code today} — what the system shows when nobody chose one. */
  public static Period current(Frequency frequency, LocalDate today) {
    return of(frequency, today);
  }

  /**
   * An interval chosen freely, with {@code end} inclusive as a person would write it: "12/03 to
   * 27/06" includes the 27th.
   */
  public static Period between(Frequency frequency, LocalDate from, LocalDate toInclusive) {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(toInclusive, "to must not be null");
    return new Period(frequency, from, toInclusive.plusDays(1));
  }

  /**
   * The last {@code days} ending on {@code anchor}, inclusive — the rolling reading of a weekly or
   * monthly period.
   *
   * <p>No new concept reaches the engine: a rolling window is an ordinary interval whose edges the
   * system computes instead of the user typing them, so every rule for intervals already applies to
   * it — including being compared against the preceding window of the same length, and never being
   * treated as a partially-elapsed bucket, since it is always its full length.
   */
  public static Period lastDays(Frequency frequency, int days, LocalDate anchor) {
    if (days < 1) {
      throw new IllegalArgumentException("janela móvel precisa de ao menos um dia: " + days);
    }
    return between(frequency, anchor.minusDays(days - 1L), anchor);
  }

  /** Whether this period's edges are exactly the calendar bucket of its frequency. */
  public boolean isBucket() {
    return start.equals(frequency.bucketStart(start))
        && end.equals(frequency.nextBucketStart(start));
  }

  public long days() {
    return ChronoUnit.DAYS.between(start, end);
  }

  /**
   * The interval immediately before this one, of the same length. For a calendar bucket that is the
   * previous bucket, so the long-standing behaviour survives without a special case; for a chosen
   * 45-day range it is the preceding 45 days, which is the only baseline that does not mix
   * durations.
   */
  public Period previous() {
    if (isBucket()) {
      LocalDate prev = frequency.previousBucketStart(start);
      return new Period(frequency, prev, start);
    }
    return new Period(frequency, start.minusDays(days()), start);
  }

  /** The interval immediately after this one, of the same length. */
  public Period next() {
    if (isBucket()) {
      return new Period(frequency, end, frequency.nextBucketStart(end));
    }
    return new Period(frequency, end, end.plusDays(days()));
  }

  /**
   * The slices this period is read in: for a calendar bucket, the {@code trailingBuckets} buckets
   * ending at it — the evolution chart that has always been shown; for a chosen interval, the
   * frequency's slices inside it.
   *
   * <p>The frequency therefore changes role rather than meaning: it picks the bucket when a bucket
   * is selected, and it sets the chart's granularity when an interval is. Both readings come out of
   * one call, so nothing downstream has to know which one it got.
   */
  public List<Bucket> slices(int trailingBuckets) {
    return isBucket() ? frequency.lastBuckets(start, trailingBuckets) : frequency.slice(start, end);
  }

  /**
   * The span of days that has to be read from storage to answer for this period: everything the
   * slices draw, plus the preceding interval the comparison needs.
   *
   * <p>Deriving it from the slices instead of a fixed count of buckets is what lets an interval and
   * a bucket share one rule — the window grows with what is actually being drawn.
   */
  public Bucket readSpan(int trailingBuckets) {
    List<Bucket> drawn = slices(trailingBuckets);
    LocalDate from = drawn.get(0).start();
    LocalDate previousStart = previous().start();
    if (previousStart.isBefore(from)) {
      from = previousStart;
    }
    LocalDate to = drawn.get(drawn.size() - 1).endExclusive();
    if (to.isBefore(end)) {
      to = end;
    }
    return new Bucket(from, to);
  }

  public boolean contains(LocalDate date) {
    return !date.isBefore(start) && date.isBefore(end);
  }

  /**
   * Whether this period is a calendar bucket still running as of {@code today}.
   *
   * <p>Only that case compares an elapsed slice: a month in progress must be measured against the
   * same slice of the month before. An interval the user chose is what they asked for, even when it
   * ends today — clipping it would answer a different question than the one posed.
   */
  public boolean inProgress(LocalDate today) {
    return isBucket() && contains(today);
  }

  /** The same anchor read at another frequency. A chosen interval keeps its edges. */
  public Period at(Frequency other) {
    return isBucket() ? of(other, start) : new Period(other, start, end);
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
