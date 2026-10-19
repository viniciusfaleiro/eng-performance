package com.engperf.application.metrics;

import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the waiting time of a population was spent being blocked by.
 *
 * <p>Lives next to {@link MetricsDrilldown} and reuses the engine's matching and period membership
 * for the same reason: the breakdown has to describe the very items the waiting phase was computed
 * over, or it explains a cohort nobody is looking at.
 *
 * <p>It describes the <strong>composition</strong> of that population's waiting hours — not a
 * decomposition of the waiting card, which is a median. A median is not the sum of anything, so
 * slices that claimed to add up to it would be inventing a reconciliation that cannot exist. The
 * total these parts compose travels with them, so the share always says what it is a share of.
 */
final class ImpedimentBreakdown {

  /** The detail-key prefixes the ingestion writes, one entry per reason. */
  private static final String HOURS = "impeded:";

  private static final String LABEL = "impeded_label:";

  /** The reason bucket for impeded time nobody labelled — the raw key the ingestion reserves. */
  private static final String NO_REASON = "?";

  private ImpedimentBreakdown() {}

  static ImpedimentReasons of(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      Predicate<RawEvent> population) {
    List<MetricsEngine.Matched> matched =
        MetricsEngine.match(index, events, def, nodeId, population, readNow);
    List<MetricsEngine.Matched> ms =
        MetricsEngine.inBucket(matched, new Bucket(period.start(), period.end()), def);

    double waiting = 0;
    int items = 0;
    int withSignal = 0;
    Map<String, Double> hours = new LinkedHashMap<>();
    Map<String, String> labels = new LinkedHashMap<>();
    for (MetricsEngine.Matched m : ms) {
      // A mediana só usa o que tem medida; a composição tem de usar o mesmo conjunto, senão
      // descreve itens que a fase ao lado não contou.
      if (!m.hasMeasure()) {
        continue;
      }
      items++;
      waiting += m.measure();
      boolean signal = false;
      for (Map.Entry<String, String> e : m.source().detail().entrySet()) {
        if (!e.getKey().startsWith(HOURS)) {
          continue;
        }
        String reason = e.getKey().substring(HOURS.length());
        double h = parse(e.getValue());
        if (h <= 0) {
          continue;
        }
        signal = true;
        hours.merge(reason, h, Double::sum);
        String label = m.source().detail().get(LABEL + reason);
        if (label != null) {
          labels.putIfAbsent(reason, label);
        }
      }
      if (signal) {
        withSignal++;
      }
    }
    return new ImpedimentReasons(slices(hours, labels, waiting), waiting, items, withSignal);
  }

  /**
   * The slices, largest first, with the residual last. The residual is what is left of the
   * population's waiting hours after every reason — derived, never accumulated, so it cannot
   * disagree with the total it completes.
   */
  private static List<ImpedimentSlice> slices(
      Map<String, Double> hours, Map<String, String> labels, double waiting) {
    List<ImpedimentSlice> out = new ArrayList<>();
    double named = 0;
    for (Map.Entry<String, Double> e : hours.entrySet()) {
      if (NO_REASON.equals(e.getKey())) {
        continue;
      }
      named += e.getValue();
      out.add(
          new ImpedimentSlice(
              e.getKey(),
              labels.getOrDefault(e.getKey(), e.getKey()),
              e.getValue(),
              share(e.getValue(), waiting)));
    }
    out.sort(Comparator.comparingDouble(ImpedimentSlice::hours).reversed());

    double unnamed = hours.getOrDefault(NO_REASON, 0.0);
    if (unnamed > 0) {
      out.add(new ImpedimentSlice(NO_REASON, "Sem motivo", unnamed, share(unnamed, waiting)));
    }
    double residual = Math.max(0, waiting - named - unnamed);
    if (residual > 0) {
      out.add(new ImpedimentSlice("queue", "Fila do board", residual, share(residual, waiting)));
    }
    return out;
  }

  private static double share(double part, double total) {
    return total <= 0 ? 0.0 : part / total * 100.0;
  }

  private static double parse(String raw) {
    try {
      return Double.parseDouble(raw);
    } catch (RuntimeException e) {
      return 0.0; // detail ilegível: a fatia não existe, em vez de derrubar a leitura
    }
  }
}
