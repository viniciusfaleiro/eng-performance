package com.engperf.application.metrics;

import com.engperf.application.port.inbound.AiDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the IA dashboard: the node's IA cards (AI share and adoption from the engine, AI impact
 * composed from the two cohort cycle-time series), the AI-adoption ranking of the node's children —
 * verticals at the overview, teams within a vertical, nothing for a team or person (coaching-only:
 * people are never compared publicly) — and the AI-vs-non-AI cycle-time series.
 */
public final class AiDashboardService implements AiDashboardUseCase {

  private static final int TOP_N = 10;
  private static final String CYCLE_TIME = "cycle_time";

  private final MetricsQueryUseCase metrics;
  private final MetricCatalog catalog;
  private final StructureRepositoryPort structure;

  public AiDashboardService(
      MetricsQueryUseCase metrics, MetricCatalog catalog, StructureRepositoryPort structure) {
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
  }

  @Override
  public AiDashboard dashboard(String nodeId, Frequency frequency) {
    Map<String, MetricCard> nodeCards = cardsByKey(nodeId, frequency);

    List<AiCard> cards = new ArrayList<>();
    for (MetricDefinition def : catalog.ia()) {
      MetricCard c = nodeCards.get(def.key());
      if (c != null) {
        cards.add(new AiCard(def, c.current(), c.coverage()));
      }
    }
    Impact impact = computeImpact(nodeId, frequency);
    cards.add(new AiCard(MetricCatalog.AI_IMPACT, impact.value(), impact.coverage()));

    List<AdoptionRank> adoption = new ArrayList<>();
    for (Child ch : children(nodeId)) {
      Map<String, MetricCard> childCards = cardsByKey(ch.id(), frequency);
      adoption.add(new AdoptionRank(ch.id(), ch.label(), value(childCards, "ai_adoption")));
    }
    adoption.sort(Comparator.comparingDouble(AdoptionRank::adoption).reversed());
    if (adoption.size() > TOP_N) {
      adoption = new ArrayList<>(adoption.subList(0, TOP_N));
    }

    return new AiDashboard(
        nodeId, childType(nodeId), cards, adoption, impact.withAi(), impact.withoutAi());
  }

  @Override
  public AiCard impact(String nodeId, Frequency frequency) {
    Impact i = computeImpact(nodeId, frequency);
    return new AiCard(MetricCatalog.AI_IMPACT, i.value(), i.coverage());
  }

  private Impact computeImpact(String nodeId, Frequency frequency) {
    List<Double> withAi = seriesValues(nodeId, frequency, true);
    List<Double> withoutAi = seriesValues(nodeId, frequency, false);
    int last = withAi.size() - 1;
    double aiNow = withAi.get(last);
    double nonNow = withoutAi.get(last);
    double aiPrev = last > 0 ? withAi.get(last - 1) : 0.0;
    double nonPrev = last > 0 ? withoutAi.get(last - 1) : 0.0;

    MetricValue value;
    if (aiNow == 0.0 || nonNow == 0.0) {
      // Only one cohort present → no meaningful comparison; report zero impact, no evolution.
      value = MetricValue.of(0.0, null, Direction.HIGHER_BETTER);
    } else {
      Double prev = (aiPrev == 0.0 || nonPrev == 0.0) ? null : fasterPct(nonPrev, aiPrev);
      value = MetricValue.of(fasterPct(nonNow, aiNow), prev, Direction.HIGHER_BETTER);
    }
    // Coverage = share of the node's PRs that are AI-assisted (AI PRs over all PRs; the two
    // cohorts partition the population).
    long ai = Math.round(lastValue(metrics.cohortSeries("throughput", nodeId, frequency, true)));
    long non = Math.round(lastValue(metrics.cohortSeries("throughput", nodeId, frequency, false)));
    return new Impact(value, new Coverage(ai, ai + non), withAi, withoutAi);
  }

  /** How much faster (%) the AI cohort's cycle time is than the non-AI cohort's. */
  private static double fasterPct(double nonAi, double ai) {
    return nonAi == 0.0 ? 0.0 : (nonAi - ai) / nonAi * 100.0;
  }

  private List<Double> seriesValues(String nodeId, Frequency frequency, boolean aiAssisted) {
    return metrics.cohortSeries(CYCLE_TIME, nodeId, frequency, aiAssisted).points().stream()
        .map(p -> p.value().value())
        .toList();
  }

  private static double lastValue(MetricSeries s) {
    List<SeriesPoint> pts = s.points();
    return pts.isEmpty() ? 0.0 : pts.get(pts.size() - 1).value().value();
  }

  private Map<String, MetricCard> cardsByKey(String nodeId, Frequency frequency) {
    Map<String, MetricCard> byKey = new LinkedHashMap<>();
    for (MetricCard card : metrics.cards(nodeId, frequency)) {
      byKey.put(card.definition().key(), card);
    }
    return byKey;
  }

  private static double value(Map<String, MetricCard> cards, String key) {
    MetricCard c = cards.get(key);
    return c == null ? 0.0 : c.current().value();
  }

  private List<Child> children(String nodeId) {
    if (nodeId == null || nodeId.equals("all")) {
      return structure.findVerticals().stream().map(v -> new Child(v.id(), v.name())).toList();
    }
    if (nodeId.startsWith("v:")) {
      return structure.findTeams().stream()
          .filter(t -> t.verticalId().equals(nodeId))
          .map(t -> new Child(t.id(), t.name()))
          .toList();
    }
    return List.of(); // team or person → no public ranking
  }

  private static String childType(String nodeId) {
    if (nodeId == null || nodeId.equals("all")) {
      return "vertical";
    }
    if (nodeId.startsWith("v:")) {
      return "team";
    }
    return null;
  }

  private record Child(String id, String label) {}

  private record Impact(
      MetricValue value, Coverage coverage, List<Double> withAi, List<Double> withoutAi) {}
}
