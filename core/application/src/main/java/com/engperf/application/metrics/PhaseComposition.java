package com.engperf.application.metrics;

import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/**
 * How much of a population's time a phase took, in hours.
 *
 * <p>Lives next to {@link MetricsDrilldown} and reuses the engine's matching and period membership
 * for the same reason it does: the sum has to be over the very items the phase was computed from,
 * or it describes a cohort nobody is looking at.
 *
 * <p>It exists because a <strong>median per phase does not add up to the median cycle</strong>. Per
 * item the phases do sum to the cycle, but the median of the parts is not the median of the whole —
 * and a phase most items skip has a median of zero no matter how long the few that passed through
 * it stayed there. That is how a board came to show 21 h of cycle beside 1,5 h of phases, with no
 * way for a reader to tell which number to believe.
 */
final class PhaseComposition {

  private PhaseComposition() {}

  /** The phase's hours summed over the population of {@code def} at {@code nodeId}. */
  static double hours(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      Predicate<RawEvent> population) {
    List<MetricsEngine.Matched> matched =
        MetricsEngine.match(index, events, def, nodeId, population, readNow);
    double total = 0;
    for (MetricsEngine.Matched m :
        MetricsEngine.inBucket(matched, new Bucket(period.start(), period.end()), def)) {
      // Só o que tem medida: ausência é "sem dado", e somá-la como zero é exatamente o erro que
      // esta leitura existe para desfazer.
      if (m.hasMeasure()) {
        total += m.measure();
      }
    }
    return total;
  }
}
