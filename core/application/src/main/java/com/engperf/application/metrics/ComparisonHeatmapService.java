package com.engperf.application.metrics;

import com.engperf.application.port.inbound.AiDashboardUseCase;
import com.engperf.application.port.inbound.ComparisonHeatmapUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.structure.Person;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the Comparativo heatmap: for each child of the node (teams/verticals at the overview,
 * teams within a vertical, people within a team, colleagues for a person) it reads that child's
 * engine cards for every catalog metric in column order and appends the composed AI impact — so a
 * cell always equals the same node's dashboard card. Relative-standing colour is left to the
 * client; the web adapter enforces scope and the coaching rule on which rows are returned.
 */
public final class ComparisonHeatmapService implements ComparisonHeatmapUseCase {

  private final MetricsQueryUseCase metrics;
  private final MetricCatalog catalog;
  private final StructureRepositoryPort structure;
  private final AiDashboardUseCase ai;

  public ComparisonHeatmapService(
      MetricsQueryUseCase metrics,
      MetricCatalog catalog,
      StructureRepositoryPort structure,
      AiDashboardUseCase ai) {
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
    this.ai = Objects.requireNonNull(ai, "ai must not be null");
  }

  @Override
  public ComparisonHeatmap heatmap(String nodeId, Frequency frequency, String scope) {
    List<MetricDefinition> columns = columns();
    List<HeatmapMetric> metricColumns =
        columns.stream().map(d -> new HeatmapMetric(d.key(), d.label(), d.unit())).toList();

    List<HeatmapRow> rows = new ArrayList<>();
    for (Row child : childRows(nodeId, scope)) {
      Map<String, MetricCard> cards = cardsByKey(child.id(), frequency);
      List<Double> values = new ArrayList<>();
      for (MetricDefinition def : columns) {
        if (def.key().equals(MetricCatalog.AI_IMPACT.key())) {
          values.add(ai.impact(child.id(), frequency).value().value());
        } else {
          MetricCard card = cards.get(def.key());
          values.add(card == null ? 0.0 : card.current().value());
        }
      }
      rows.add(new HeatmapRow(child.id(), child.label(), child.rowType(), values));
    }
    return new ComparisonHeatmap(nodeId, metricColumns, rows);
  }

  /** All heatmap columns: the three groups in catalog order, then the composed AI impact. */
  private List<MetricDefinition> columns() {
    List<MetricDefinition> cols = new ArrayList<>();
    cols.addAll(catalog.dora());
    cols.addAll(catalog.fluxo());
    cols.addAll(catalog.ia());
    cols.add(MetricCatalog.AI_IMPACT);
    return cols;
  }

  private Map<String, MetricCard> cardsByKey(String nodeId, Frequency frequency) {
    Map<String, MetricCard> byKey = new LinkedHashMap<>();
    for (MetricCard card : metrics.cards(nodeId, frequency)) {
      byKey.put(card.definition().key(), card);
    }
    return byKey;
  }

  private List<Row> childRows(String nodeId, String scope) {
    if (nodeId == null || nodeId.equals("all")) {
      if ("verticais".equalsIgnoreCase(scope)) {
        return structure.findVerticals().stream()
            .map(v -> new Row(v.id(), v.name(), "Vertical"))
            .toList();
      }
      return structure.findTeams().stream().map(t -> new Row(t.id(), t.name(), "Time")).toList();
    }
    if (nodeId.startsWith("v:")) {
      return structure.findTeams().stream()
          .filter(t -> nodeId.equals(t.verticalId()))
          .map(t -> new Row(t.id(), t.name(), "Time"))
          .toList();
    }
    if (nodeId.startsWith("t:")) {
      return peopleOfTeam(nodeId);
    }
    if (nodeId.startsWith("p:")) {
      return structure
          .findPerson(nodeId)
          .flatMap(Person::currentTeamId)
          .map(this::peopleOfTeam)
          .orElse(List.of());
    }
    return List.of();
  }

  private List<Row> peopleOfTeam(String teamId) {
    return structure.findPeople().stream()
        .filter(p -> p.currentTeamId().map(teamId::equals).orElse(false))
        .map(p -> new Row(p.id(), p.name(), "Pessoa"))
        .toList();
  }

  private record Row(String id, String label, String rowType) {}
}
