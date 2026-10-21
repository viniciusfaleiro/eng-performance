package com.engperf.application.metrics;

import com.engperf.application.port.inbound.FlowDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the Fluxo dashboard: the node's Fluxo cards followed by the volume cards (value +
 * evolution + coverage, no tier), the four-phase cycle-time breakdown (median of each phase over
 * the node's PR population) with what the waiting phase was spent blocked by, and a
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
  public FlowDashboard dashboard(
      String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers) {
    Map<String, MetricCard> nodeCards = cardsByKey(nodeId, period, workTypes, containers);

    List<FlowCard> cards = new ArrayList<>();
    // Fluxo first, then the volume metrics — volume is context, it must not compete with the
    // delivery headline.
    for (MetricDefinition def : concat(catalog.fluxo(), catalog.volume())) {
      MetricCard c = nodeCards.get(def.key());
      if (c != null && MetricAvailability.readableAt(def, period.frequency())) {
        cards.add(FlowCard.of(def, c));
      }
    }

    List<PhaseSlice> phases = phases(nodeId, period, workTypes, containers, nodeCards);

    List<Child> children = children(nodeId);
    List<ScatterPoint> scatter = new ArrayList<>();
    for (Child ch : children) {
      Map<String, MetricCard> childCards = cardsByKey(ch.id(), period, workTypes, containers);
      scatter.add(
          new ScatterPoint(
              ch.id(),
              ch.label(),
              value(childCards, "throughput"),
              value(childCards, "cycle_time")));
    }

    return new FlowDashboard(
        nodeId,
        childType(nodeId),
        cards,
        phases,
        // Escalado para a Espera que o board mostra: as fatias têm de somar o número do qual a
        // gaveta foi aberta, senão a tela põe duas escalas lado a lado sem dizer qual é qual.
        metrics
            .impediments(nodeId, period, workTypes, containers)
            .scaledTo(phaseValue(phases, "waiting_time")),
        scatter);
  }

  /**
   * Cada fase é <strong>a participação dela no tempo dos itens, aplicada ao Cycle Time do
   * card</strong> — por isso as três somam o card, que é o que o leitor confere.
   *
   * <p>Era mediana por fase, e mediana das partes não é a mediana do todo: a fase que a maioria dos
   * itens pula tem mediana zero por mais tempo que os poucos que passaram por ela tenham ficado lá.
   * Daí 21 h de ciclo ao lado de 1,5 h de fases, sem nada na tela dizendo qual acreditar.
   *
   * <p>Soma zero — ou card ausente — devolve as três em zero: sem horas não há participação, e
   * inventar uma divisão aqui seria devolver um número que não veio de dado nenhum.
   */
  private List<PhaseSlice> phases(
      String nodeId,
      Period period,
      WorkTypeFilter workTypes,
      ContainerFilter containers,
      Map<String, MetricCard> nodeCards) {
    MetricCard cycle = nodeCards.get("cycle_time");
    Map<String, Double> hours = metrics.phaseHours(nodeId, period, workTypes, containers);
    double total = hours.values().stream().mapToDouble(Double::doubleValue).sum();
    List<PhaseSlice> phases = new ArrayList<>();
    for (MetricDefinition def : catalog.phases()) {
      double share = total <= 0 ? 0.0 : hours.getOrDefault(def.key(), 0.0) / total;
      double value = cycle == null ? 0.0 : cycle.current().value() * share;
      phases.add(new PhaseSlice(def.key(), def.label(), value, share * 100.0));
    }
    return phases;
  }

  private static double phaseValue(List<PhaseSlice> phases, String key) {
    return phases.stream()
        .filter(p -> p.key().equals(key))
        .mapToDouble(PhaseSlice::hours)
        .findFirst()
        .orElse(0.0);
  }

  private static List<MetricDefinition> concat(
      List<MetricDefinition> first, List<MetricDefinition> second) {
    List<MetricDefinition> out = new ArrayList<>(first);
    out.addAll(second);
    return out;
  }

  private Map<String, MetricCard> cardsByKey(
      String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers) {
    Map<String, MetricCard> byKey = new LinkedHashMap<>();
    for (MetricCard card : metrics.cards(nodeId, period, workTypes, containers)) {
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
