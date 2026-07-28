package com.engperf.application.metrics;

import com.engperf.application.port.inbound.FlowDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the Fluxo dashboard: the node's Fluxo cards (value + evolution + coverage, no tier), the
 * four-phase cycle-time breakdown (median of each phase over the node's PR population), and a
 * throughput×cycle scatter of the node's children — verticals at the overview, teams within a
 * vertical, nothing for a team or person (coaching-only: people are never compared publicly).
 */
public final class FlowDashboardService implements FlowDashboardUseCase {

  private final MetricsQueryUseCase metrics;
  private final MetricCatalog catalog;
  private final StructureRepositoryPort structure;

  public FlowDashboardService(
      MetricsQueryUseCase metrics, MetricCatalog catalog, StructureRepositoryPort structure) {
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
  }

  @Override
  public FlowDashboard dashboard(String nodeId, Frequency frequency) {
    Map<String, MetricCard> nodeCards = cardsByKey(nodeId, frequency);

    List<FlowCard> cards = new ArrayList<>();
    for (MetricDefinition def : catalog.fluxo()) {
      MetricCard c = nodeCards.get(def.key());
      if (c != null) {
        cards.add(new FlowCard(def, c.current(), c.coverage()));
      }
    }

    List<PhaseSlice> phases = new ArrayList<>();
    for (MetricDefinition def : catalog.phases()) {
      MetricCard c = nodeCards.get(def.key());
      if (c != null) {
        phases.add(new PhaseSlice(def.key(), def.label(), c.current().value()));
      }
    }

    List<Child> children = children(nodeId);
    List<ScatterPoint> scatter = new ArrayList<>();
    for (Child ch : children) {
      Map<String, MetricCard> childCards = cardsByKey(ch.id(), frequency);
      scatter.add(
          new ScatterPoint(
              ch.id(),
              ch.label(),
              value(childCards, "throughput"),
              value(childCards, "cycle_time")));
    }

    return new FlowDashboard(nodeId, childType(nodeId), cards, phases, scatter);
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
    return List.of(); // team or person → no public scatter
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
}
