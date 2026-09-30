package com.engperf.application.metrics;

import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The item list behind a displayed number: which events the metric considered, and which of them
 * the value actually used.
 *
 * <p>Lives next to {@link MetricsEngine} and reuses its matching and its period membership rather
 * than re-deriving either. A list assembled by a second rule would be a second source of truth, and
 * the two would drift — which is the whole point of a drill-down not happening.
 *
 * <p>Split out of the engine when the engine outgrew the file-length limit. The cut is along a real
 * seam: everything here answers "which items, and why each counted", while what stayed answers
 * "what is the number".
 */
final class MetricsDrilldown {

  private MetricsDrilldown() {}

  /**
   * The raw events considered for {@code def} at {@code nodeId} in the bucket starting at {@code
   * bucketStart} — the exact same attribution/population {@link MetricsEngine#series} uses, so this
   * can never list something the displayed value didn't actually use. Each item is flagged {@code
   * counted} exactly as {@link MetricsEngine}'s aggregation would use it, mirrored case-by-case per
   * {@link com.engperf.domain.metrics.Aggregation} so the list never drifts from the number it
   * explains.
   */
  static List<MetricDrilldownItem> items(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      Predicate<RawEvent> population) {
    List<MetricsEngine.Matched> matched =
        MetricsEngine.match(index, events, def, nodeId, population, readNow);
    Bucket bucket = new Bucket(period.start(), period.end());
    List<MetricsEngine.Matched> ms = MetricsEngine.inBucket(matched, bucket, def);
    return select(def, ms).stream().map(s -> toItem(def, s)).toList();
  }

  private record Selected(MetricsEngine.Matched matched, boolean counted, String excludedReason) {}

  private static List<Selected> select(MetricDefinition def, List<MetricsEngine.Matched> ms) {
    return switch (def.aggregation()) {
      // SUM/RATIO/DISTINCT_RATIO: aggregate() uses every matched event, so every item counts.
      case SUM, RATIO, DISTINCT_RATIO -> ms.stream().map(m -> new Selected(m, true, null)).toList();
      // MEDIAN: aggregate() filters to Matched::hasMeasure — mirror that filter here.
      case MEDIAN ->
          ms.stream()
              .map(m -> new Selected(m, m.hasMeasure(), m.hasMeasure() ? null : "sem medida"))
              .toList();
      case SNAPSHOT -> selectSnapshot(ms);
    };
  }

  /** Mirrors the snapshot aggregation: only each entity's latest measured event counts. */
  private static List<Selected> selectSnapshot(List<MetricsEngine.Matched> ms) {
    Map<String, MetricsEngine.Matched> latestByEntity = new HashMap<>();
    for (MetricsEngine.Matched m : ms) {
      if (!m.hasMeasure()) {
        continue;
      }
      MetricsEngine.Matched cur = latestByEntity.get(m.entity());
      if (cur == null || m.at().isAfter(cur.at())) {
        latestByEntity.put(m.entity(), m);
      }
    }
    List<Selected> out = new ArrayList<>();
    for (MetricsEngine.Matched m : ms) {
      if (!m.hasMeasure()) {
        out.add(new Selected(m, false, "sem medida"));
        continue;
      }
      boolean counted = latestByEntity.get(m.entity()) == m;
      out.add(
          new Selected(
              m, counted, counted ? null : "superado por evento mais recente da mesma entidade"));
    }
    return out;
  }

  private static MetricDrilldownItem toItem(MetricDefinition def, Selected s) {
    MetricsEngine.Matched m = s.matched();
    RawEvent e = m.source();
    String label = e.detail().getOrDefault("summary", "");
    if (label.isBlank()) {
      label = e.id();
    }
    return new MetricDrilldownItem(
        e.id(),
        e.type(),
        e.detail().getOrDefault("url", ""),
        label,
        m.entity(),
        e.occurredAt(),
        contribution(def, m, s),
        s.counted(),
        s.excludedReason());
  }

  /**
   * What this event actually added to the aggregated value. A SUM counts events ({@code
   * aggregate()} returns {@code ms.size()}), so a counted event contributes exactly 1 — reporting
   * its raw measure would show 0 for events that carry no numeric value, like a commit. Every other
   * aggregation reads the measure itself.
   */
  private static double contribution(MetricDefinition def, MetricsEngine.Matched m, Selected s) {
    if (def.aggregation() == Aggregation.SUM) {
      return s.counted() ? 1 : 0;
    }
    return m.measure();
  }
}
