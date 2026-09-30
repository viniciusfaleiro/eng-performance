package com.engperf.domain.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The stretches of time a work item spent actually being worked on, reconstructed by the ingestion
 * from the item's own state history.
 *
 * <p>This type answers one question — <em>which intervals was this item in progress?</em> — and it
 * is the only place that answers it. Two callers need it for different reasons: the work
 * distribution divides those hours among concurrent items, and an interval metric decides which
 * periods the item belongs to. Parsing the format twice would be two chances to disagree, and they
 * would have disagreed immediately: the distribution used to stop an open item at the clock of the
 * ingestion while the engine needs it to reach the clock of the reading.
 *
 * <p>The intervals have <strong>gaps</strong>, and the gaps matter. An item worked from the 1st to
 * the 3rd and again from the 20th to the 22nd is not in progress on the 15th, so collapsing the
 * whole thing to first-start..last-end would count a period nobody touched. Every test here walks
 * the intervals rather than their outer bounds.
 */
public record InProgressSpans(List<Interval> intervals) {

  /** Half-open {@code [start, end)} in epoch milliseconds. */
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

  private static final InProgressSpans NONE = new InProgressSpans(List.of());

  public InProgressSpans {
    intervals = List.copyOf(intervals);
  }

  /**
   * Reads an item's in-progress intervals.
   *
   * <p>An item that never reached a terminal state has its last interval closed at the ingestion's
   * clock, because that is all the ingestion could know. {@code readNow} reopens it: the interval
   * is extended to the moment of the reading, so "in progress today" means what the reader means by
   * it and not "in progress when the scheduler last ran". Without this, a daily reading silently
   * reports the freshness of the sync instead of the state of the board.
   *
   * <p>What it assumes, and can get wrong: that the item really is still open now. If the sync ran
   * three days ago and the task was closed yesterday, this reports it as in progress. That is
   * staleness, not a miscalculation, and the alternative is worse — trusting the ingestion's clock
   * empties the daily reading every time the scheduler is late.
   *
   * <p>An item with no usable state history has no intervals; that is "no data", and callers must
   * keep it out of their numbers rather than counting it as zero.
   */
  public static InProgressSpans of(RawEvent item, Instant readNow) {
    Objects.requireNonNull(item, "item must not be null");
    Objects.requireNonNull(readNow, "readNow must not be null");
    String raw = item.detail().get("spans");
    if (raw == null || raw.isBlank()) {
      return NONE;
    }
    boolean open = "1".equals(item.detail().get("in_progress"));
    List<Interval> parsed = new ArrayList<>();
    long lastEnd = Long.MIN_VALUE;
    int lastIndex = -1;
    for (String part : raw.split(",")) {
      int colon = part.indexOf(':');
      if (colon < 0) {
        continue; // formato inesperado: ignora a parte em vez de derrubar a leitura inteira
      }
      try {
        long start = Long.parseLong(part.substring(0, colon).strip());
        long end = Long.parseLong(part.substring(colon + 1).strip());
        if (start >= end) {
          continue;
        }
        parsed.add(new Interval(start, end));
        if (end > lastEnd) {
          lastEnd = end;
          lastIndex = parsed.size() - 1;
        }
      } catch (NumberFormatException ignored) {
        // idem: uma parte ilegível não invalida as outras
      }
    }
    if (parsed.isEmpty()) {
      return NONE;
    }
    if (open) {
      long now = readNow.toEpochMilli();
      Interval last = parsed.get(lastIndex);
      if (now > last.end()) {
        parsed.set(lastIndex, new Interval(last.start(), now));
      }
    }
    return new InProgressSpans(parsed);
  }

  public boolean isEmpty() {
    return intervals.isEmpty();
  }

  /**
   * Whether any interval overlaps {@code [from, to)} — the membership test of an interval metric.
   */
  public boolean overlaps(Instant from, Instant to) {
    long lo = from.toEpochMilli();
    long hi = to.toEpochMilli();
    for (Interval i : intervals) {
      if (i.start() < hi && i.end() > lo) {
        return true;
      }
    }
    return false;
  }

  /** The intervals clipped to {@code [from, to)}; empty when none of them reaches into it. */
  public List<Interval> within(Instant from, Instant to) {
    long lo = from.toEpochMilli();
    long hi = to.toEpochMilli();
    List<Interval> out = new ArrayList<>();
    for (Interval i : intervals) {
      long start = Math.max(i.start(), lo);
      long end = Math.min(i.end(), hi);
      if (end > start) {
        out.add(new Interval(start, end));
      }
    }
    return out;
  }

  /** Total elapsed hours in progress inside {@code [from, to)}. */
  public double hoursWithin(Instant from, Instant to) {
    double total = 0;
    for (Interval i : within(from, to)) {
      total += i.hours();
    }
    return total;
  }
}
