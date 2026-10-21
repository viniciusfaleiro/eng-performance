package com.engperf.domain.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Set arithmetic over {@link Interval}s — the one implementation of "these stretches of time, taken
 * together".
 *
 * <p>It exists because the same arithmetic is needed on both sides of the ingestion boundary: the
 * Azure DevOps adapter unites two impediment signals and asks how much of a state interval they
 * cover, and the individual panel subtracts the impeded stretches from the ones it prorates. Two
 * implementations of "overlap" would be two chances to disagree about the same hour, and the
 * disagreement would surface as a distribution that does not add up to the card above it.
 *
 * <p>Every method takes and returns intervals that are <strong>disjoint and ordered</strong> once
 * they come out of {@link #union(List)}; the inputs need not be.
 */
public final class Intervals {

  private Intervals() {}

  /**
   * The {@code "start:end,start:end"} form the ingestion writes, in the order it wrote them.
   *
   * <p>An unreadable part is skipped rather than failing the whole string: one malformed stretch
   * costs its own hours, while refusing the string costs every stretch the item ever had.
   */
  public static List<Interval> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<Interval> out = new ArrayList<>();
    for (String part : raw.split(",")) {
      int colon = part.indexOf(':');
      if (colon < 0) {
        continue;
      }
      try {
        long start = Long.parseLong(part.substring(0, colon).strip());
        long end = Long.parseLong(part.substring(colon + 1).strip());
        if (start < end) {
          out.add(new Interval(start, end));
        }
      } catch (NumberFormatException ignored) {
        // idem: uma parte ilegível não invalida as outras
      }
    }
    return List.copyOf(out);
  }

  /** The input merged into disjoint, ordered intervals. Touching intervals become one. */
  public static List<Interval> union(List<Interval> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<Interval> sorted = new ArrayList<>(raw);
    sorted.sort(Comparator.comparingLong(Interval::start).thenComparingLong(Interval::end));
    List<Interval> out = new ArrayList<>();
    long start = sorted.get(0).start();
    long end = sorted.get(0).end();
    for (Interval i : sorted.subList(1, sorted.size())) {
      if (i.start() > end) {
        out.add(new Interval(start, end));
        start = i.start();
        end = i.end();
      } else if (i.end() > end) {
        // Encostado ou sobreposto: estende. Encostado conta como contínuo de propósito — um sinal
        // retirado e reposto no mesmo instante é um registro, não uma pausa.
        end = i.end();
      }
    }
    out.add(new Interval(start, end));
    return List.copyOf(out);
  }

  /** How much of {@code [from, to)} the intervals cover, in milliseconds. */
  public static long overlapMs(List<Interval> intervals, long from, long to) {
    long total = 0;
    for (Interval i : intervals) {
      long lo = Math.max(i.start(), from);
      long hi = Math.min(i.end(), to);
      if (hi > lo) {
        total += hi - lo;
      }
    }
    return total;
  }

  /** The intervals clipped to {@code [from, to)}; empty when none of them reaches into it. */
  public static List<Interval> clip(List<Interval> intervals, long from, long to) {
    List<Interval> out = new ArrayList<>();
    for (Interval i : intervals) {
      long lo = Math.max(i.start(), from);
      long hi = Math.min(i.end(), to);
      if (hi > lo) {
        out.add(new Interval(lo, hi));
      }
    }
    return List.copyOf(out);
  }

  /**
   * {@code base} cut at every boundary {@code cuts} introduces, so each resulting stretch is
   * covered by a constant set of them.
   *
   * <p>It is what lets a stretch be divided among whoever covers it: without the cut, a stretch
   * half-covered by one party and wholly by another has no single answer to "how many cover this?".
   */
  public static List<Interval> slice(List<Interval> base, Collection<List<Interval>> cuts) {
    TreeSet<Long> marks = new TreeSet<>();
    for (Interval i : base) {
      marks.add(i.start());
      marks.add(i.end());
    }
    for (List<Interval> cut : cuts) {
      for (Interval i : cut) {
        marks.add(i.start());
        marks.add(i.end());
      }
    }
    List<Interval> out = new ArrayList<>();
    Long[] points = marks.toArray(new Long[0]);
    for (int i = 0; i + 1 < points.length; i++) {
      Interval candidate = new Interval(points[i], points[i + 1]);
      if (covers(base, candidate)) {
        out.add(candidate);
      }
    }
    return List.copyOf(out);
  }

  /** Whether some interval of {@code intervals} contains {@code slice} whole. */
  public static boolean covers(List<Interval> intervals, Interval slice) {
    for (Interval i : intervals) {
      if (i.start() <= slice.start() && i.end() >= slice.end()) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code base} with every stretch covered by {@code cut} removed. One interval of the base can
   * come back as several — a stretch blocked in the middle leaves the two ends.
   */
  public static List<Interval> subtract(List<Interval> base, List<Interval> cut) {
    List<Interval> holes = union(cut);
    if (holes.isEmpty()) {
      return List.copyOf(base);
    }
    List<Interval> out = new ArrayList<>();
    for (Interval b : base) {
      long cursor = b.start();
      for (Interval h : holes) {
        if (h.end() <= cursor || h.start() >= b.end()) {
          continue;
        }
        if (h.start() > cursor) {
          out.add(new Interval(cursor, h.start()));
        }
        cursor = Math.max(cursor, h.end());
        if (cursor >= b.end()) {
          break;
        }
      }
      if (cursor < b.end()) {
        out.add(new Interval(cursor, b.end()));
      }
    }
    return List.copyOf(out);
  }
}
