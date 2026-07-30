package com.engperf.application.metrics;

import com.engperf.application.metrics.StructureIndex.Attribution;
import com.engperf.domain.metrics.Aggregations;
import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * On-read aggregation. Collects every event that attributes (as-of-event) to the queried node and
 * aggregates the population directly — median/ratio are never composed from children. Handles
 * bucketing, correct-polarity evolution, the partial current period, and coverage. A population
 * predicate can restrict the metric to a cohort of events selected by an attribute (e.g. the AI
 * flag), so the same metric can be evaluated over disjoint cohorts of the same node.
 */
public final class MetricsEngine {

  private MetricsEngine() {}

  private record Matched(
      LocalDate date,
      Instant at,
      double measure,
      boolean hasMeasure,
      double numerator,
      double denominator,
      String entity) {}

  public static MetricSeries series(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Frequency freq,
      LocalDate reference,
      int bucketCount) {
    return series(index, events, def, nodeId, freq, reference, bucketCount, e -> true);
  }

  /** As {@link #series}, but only events matching {@code population} feed the metric. */
  public static MetricSeries series(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Frequency freq,
      LocalDate reference,
      int bucketCount,
      Predicate<RawEvent> population) {

    List<Matched> matched = match(index, events, def, nodeId, population);
    List<Bucket> buckets = freq.lastBuckets(reference, bucketCount);

    double[] values = new double[buckets.size()];
    for (int i = 0; i < buckets.size(); i++) {
      values[i] = aggregate(def, inBucket(matched, buckets.get(i)));
    }

    int last = buckets.size() - 1;
    Bucket current = buckets.get(last);
    int fullDays = (int) (current.endExclusive().toEpochDay() - current.start().toEpochDay());
    int elapsed = freq.elapsedDays(current.start(), reference);
    boolean partial = elapsed < fullDays;

    List<SeriesPoint> points = new ArrayList<>();
    for (int i = 0; i < buckets.size(); i++) {
      Double previous;
      if (i == 0) {
        previous = null;
      } else if (i == last && partial) {
        // Compare the elapsed slice against the same elapsed slice of the previous bucket.
        LocalDate prevStart = buckets.get(i - 1).start();
        Bucket sameSlice = new Bucket(prevStart, prevStart.plusDays(elapsed));
        previous = aggregate(def, inBucket(matched, sameSlice));
      } else {
        previous = values[i - 1];
      }
      points.add(
          new SeriesPoint(
              buckets.get(i).start().toString(),
              MetricValue.of(values[i], previous, def.direction())));
    }

    return new MetricSeries(def, points, coverage(index, events, def, population));
  }

  public static MetricCard card(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Frequency freq,
      LocalDate reference,
      int bucketCount) {
    return card(index, events, def, nodeId, freq, reference, bucketCount, e -> true);
  }

  /** As {@link #card}, but only events matching {@code population} feed the metric. */
  public static MetricCard card(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Frequency freq,
      LocalDate reference,
      int bucketCount,
      Predicate<RawEvent> population) {
    MetricSeries s = series(index, events, def, nodeId, freq, reference, bucketCount, population);
    SeriesPoint lastPoint = s.points().get(s.points().size() - 1);
    return new MetricCard(def, lastPoint.value(), s.coverage());
  }

  public static Coverage coverage(
      StructureIndex index, List<RawEvent> events, MetricDefinition def) {
    return coverage(index, events, def, e -> true);
  }

  private static Coverage coverage(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      Predicate<RawEvent> population) {
    long total = 0;
    long attributed = 0;
    for (RawEvent e : events) {
      if (!population.test(e)) {
        continue;
      }
      total++;
      if (index.attribute(e, def.scope()).isPresent()) {
        attributed++;
      }
    }
    return new Coverage(attributed, total);
  }

  private static List<Matched> match(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Predicate<RawEvent> population) {
    List<Matched> matched = new ArrayList<>();
    for (RawEvent e : events) {
      if (!population.test(e)) {
        continue;
      }
      Optional<Attribution> attr = index.attribute(e, def.scope());
      if (attr.isPresent() && attr.get().belongsTo(nodeId)) {
        Measure measure = measure(e, def.measure());
        matched.add(
            new Matched(
                e.occurredOn(),
                e.occurredAt(),
                measure.value(),
                measure.present(),
                detail(e, "num", e.ai() ? 1.0 : 0.0),
                detail(e, "den", 1.0),
                attr.get().entityKey()));
      }
    }
    return matched;
  }

  private record Measure(double value, boolean present) {}

  /**
   * Resolves the per-event measure a metric reads: the event's numeric value ({@code "value"}) or a
   * named detail key. Absent measures are flagged so median/snapshot can exclude them.
   */
  private static Measure measure(RawEvent e, String measureKey) {
    if (MetricDefinition.VALUE.equals(measureKey)) {
      return new Measure(e.value(), e.numericValue() != null);
    }
    String raw = e.detail().get(measureKey);
    if (raw == null) {
      return new Measure(0.0, false);
    }
    try {
      return new Measure(Double.parseDouble(raw), true);
    } catch (NumberFormatException ex) {
      return new Measure(0.0, false);
    }
  }

  /** Reads a numeric field from the event detail, falling back to {@code fallback} when absent. */
  private static double detail(RawEvent e, String key, double fallback) {
    String v = e.detail().get(key);
    if (v == null) {
      return fallback;
    }
    try {
      return Double.parseDouble(v);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static List<Matched> inBucket(List<Matched> matched, Bucket bucket) {
    List<Matched> out = new ArrayList<>();
    for (Matched m : matched) {
      if (bucket.contains(m.date())) {
        out.add(m);
      }
    }
    return out;
  }

  private static double aggregate(MetricDefinition def, List<Matched> ms) {
    return switch (def.aggregation()) {
      // SUM counts events (throughput = # PRs, deploy_freq = # deploys). MEDIAN/SNAPSHOT read the
      // metric's declared measure and exclude events that lack it (e.g. MTTR only over recovery
      // deploys); RATIO reads num/den, so one event can feed several metrics.
      case SUM -> ms.size();
      case MEDIAN ->
          Aggregations.median(
              ms.stream().filter(Matched::hasMeasure).mapToDouble(Matched::measure).toArray());
      case RATIO ->
          Aggregations.ratio(
              ms.stream().mapToDouble(Matched::numerator).sum(),
              ms.stream().mapToDouble(Matched::denominator).sum());
      case SNAPSHOT -> snapshot(ms);
      // DISTINCT_RATIO: distinct people with a matching event (numerator > 0) over distinct people
      // with any event — each person counted once regardless of how many events they produced.
      case DISTINCT_RATIO -> distinctRatio(ms);
    };
  }

  private static double distinctRatio(List<Matched> ms) {
    Set<String> active = new HashSet<>();
    Set<String> matching = new HashSet<>();
    for (Matched m : ms) {
      active.add(m.entity());
      if (m.numerator() > 0) {
        matching.add(m.entity());
      }
    }
    return Aggregations.ratio(matching.size(), active.size());
  }

  /**
   * Value at the end of the bucket: each entity's latest measured event, summed across entities.
   */
  private static double snapshot(List<Matched> ms) {
    Map<String, Matched> latestByEntity = new HashMap<>();
    for (Matched m : ms) {
      if (!m.hasMeasure()) {
        continue;
      }
      Matched cur = latestByEntity.get(m.entity());
      if (cur == null || m.at().isAfter(cur.at())) {
        latestByEntity.put(m.entity(), m);
      }
    }
    return latestByEntity.values().stream().mapToDouble(Matched::measure).sum();
  }
}
