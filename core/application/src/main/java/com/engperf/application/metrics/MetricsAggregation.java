package com.engperf.application.metrics;

import com.engperf.application.metrics.MetricsEngine.Matched;
import com.engperf.domain.metrics.Aggregations;
import com.engperf.domain.metrics.MetricDefinition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a bucket's matched events become one number, per {@link
 * com.engperf.domain.metrics.Aggregation}. Separate from {@link MetricsEngine} only by size: the
 * engine decides <em>which</em> events a period contains, this decides what they add up to.
 */
final class MetricsAggregation {

  private MetricsAggregation() {}

  static double of(MetricDefinition def, List<Matched> ms) {
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
