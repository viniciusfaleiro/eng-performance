package com.engperf.application.metrics;

import com.engperf.application.port.inbound.DoraDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.Benchmark;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the DORA dashboard: the node's four DORA cards (value + tier + evolution + coverage) and
 * a ranking of the node's children — verticals at the overview, teams within a vertical, nothing
 * for a team or person (coaching-only: people are never ranked). Rows are ordered by the primary
 * DORA metric respecting its direction.
 */
public final class DoraDashboardService implements DoraDashboardUseCase {

  private static final int TOP_N = 10;

  private final MetricsQueryUseCase metrics;
  private final MetricCatalog catalog;
  private final StructureRepositoryPort structure;
  private final Clock clock;

  public DoraDashboardService(
      MetricsQueryUseCase metrics,
      MetricCatalog catalog,
      StructureRepositoryPort structure,
      Clock clock) {
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public DoraDashboard dashboard(String nodeId, Frequency frequency) {
    int bucketDays = bucketDays(frequency);
    List<DoraCard> cards = cardsFor(nodeId, frequency, bucketDays);

    List<Child> children = children(nodeId);
    String childType = childType(nodeId);
    List<RankingRow> ranking = new ArrayList<>();
    for (Child c : children) {
      ranking.add(new RankingRow(c.id(), c.label(), cardsFor(c.id(), frequency, bucketDays)));
    }
    ranking.sort(rankingOrder());
    if (ranking.size() > TOP_N) {
      ranking = new ArrayList<>(ranking.subList(0, TOP_N));
    }
    return new DoraDashboard(nodeId, childType, cards, ranking);
  }

  private List<DoraCard> cardsFor(String nodeId, Frequency frequency, int bucketDays) {
    Map<String, MetricCard> byKey = new LinkedHashMap<>();
    for (MetricCard card : metrics.cards(nodeId, frequency)) {
      byKey.put(card.definition().key(), card);
    }
    List<DoraCard> cards = new ArrayList<>();
    for (MetricDefinition def : catalog.dora()) {
      MetricCard card = byKey.get(def.key());
      if (card == null) {
        continue;
      }
      double value = card.current().value();
      var tier = Benchmark.classify(def, value, bucketDays).orElse(null);
      cards.add(new DoraCard(def, card.current(), tier, card.coverage()));
    }
    return cards;
  }

  /** Order rows best-first by the primary DORA metric (the first in the catalog order). */
  private Comparator<RankingRow> rankingOrder() {
    MetricDefinition primary = catalog.dora().get(0);
    Comparator<RankingRow> byValue =
        Comparator.comparingDouble(row -> metricValue(row, primary.key()));
    return primary.direction() == Direction.HIGHER_BETTER ? byValue.reversed() : byValue;
  }

  private static double metricValue(RankingRow row, String key) {
    return row.cards().stream()
        .filter(c -> c.definition().key().equals(key))
        .mapToDouble(c -> c.value().value())
        .findFirst()
        .orElse(0.0);
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
    return List.of(); // team or person → no ranking
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

  private int bucketDays(Frequency frequency) {
    LocalDate start = frequency.bucketStart(LocalDate.now(clock));
    return (int) (frequency.nextBucketStart(start).toEpochDay() - start.toEpochDay());
  }

  private record Child(String id, String label) {}
}
