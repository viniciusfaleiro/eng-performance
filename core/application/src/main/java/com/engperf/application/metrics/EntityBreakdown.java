package com.engperf.application.metrics;

import com.engperf.application.metrics.MetricsEngine.Matched;
import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Who is behind a metric that counts distinct entities instead of events.
 *
 * <p>Runs over the same bucket, population and as-of-event attribution the card used — reusing
 * {@link MetricsEngine}'s own matching rather than re-deriving it, so the listing explains the
 * number instead of merely accompanying it. A breakdown that could disagree with the figure above
 * it would be worse than no breakdown.
 */
final class EntityBreakdown {

  private EntityBreakdown() {}

  /**
   * Every entity with at least one event in the period, ordered from the lowest share upwards.
   *
   * <p>Entities with no event are absent on purpose: the ratio divides by whoever produced
   * something, and somebody who wrote no code in a period is not evidence about AI use — reporting
   * them as a non-user would turn absence into an accusation.
   */
  static List<EntityShare> of(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      Predicate<RawEvent> population) {
    List<Matched> ms =
        MetricsEngine.inBucket(
            MetricsEngine.match(index, events, def, nodeId, population, readNow),
            new Bucket(period.start(), period.end()),
            def);
    Map<String, long[]> byEntity = new LinkedHashMap<>(); // [matching, total]
    for (Matched m : ms) {
      long[] counts = byEntity.computeIfAbsent(m.entity(), k -> new long[2]);
      counts[0] += m.numerator() > 0 ? 1 : 0;
      counts[1]++;
    }
    return byEntity.entrySet().stream()
        .map(e -> new EntityShare(e.getKey(), e.getKey(), e.getValue()[0], e.getValue()[1]))
        // Crescente, e as duas pontas saem da mesma lista — é o que garante que elas somem à
        // população que o card divide. Desempate por volume e id para a ordem ser estável.
        .sorted(
            Comparator.comparingDouble(EntityShare::share)
                .thenComparing(Comparator.comparingLong(EntityShare::total).reversed())
                .thenComparing(EntityShare::entityId))
        .toList();
  }
}
