package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A seleção de contêiner tem de alcançar <strong>todas</strong> as leituras de work item.
 *
 * <p>A lacuna que motivou estes testes passou porque cada tela foi plumbada à parte e nenhum teste
 * cruzava leitura com seleção: Fluxo e métricas respondiam, Comparativo e painel individual
 * ignoravam em silêncio — e a tela mandava o filtro para os quatro. Uma leitura que conta work item
 * e não responde é indistinguível, na tela, de uma em que a seleção não mudou nada.
 */
class ContainerSelectionReachTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final Period JUNE = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 6, 15));

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final FakeAccounts accounts = new FakeAccounts();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService metrics = new MetricsService(structure, events, catalog, CLOCK);
  private final FlowDashboardService flow = new FlowDashboardService(metrics, catalog, structure);
  private final AiDashboardService ai = new AiDashboardService(metrics, catalog, structure);
  private final ComparisonHeatmapService heatmap =
      new ComparisonHeatmapService(metrics, catalog, structure, ai);
  private final IndividualDashboardService individual =
      new IndividualDashboardService(structure, events, metrics, accounts, CLOCK);

  @BeforeEach
  void structure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2025, 1, 1)));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    // Uma mãe e uma filha, ambas concluídas no período: o throughput de "tudo" é 2, o de cada lado
    // da linha é 1. Qualquer leitura que ignore a seleção devolve 2 nos três casos.
    events.add(item("wi:1", null, 10));
    events.add(item("wi:2", "wi:1", 6));
  }

  private double throughput(ContainerFilter c) {
    return flow.dashboard("t:checkout", JUNE, WorkTypeFilter.none(), c).cards().stream()
        .filter(card -> card.definition().key().equals("throughput"))
        .findFirst()
        .orElseThrow()
        .value()
        .value();
  }

  private double heatmapThroughput(ContainerFilter c) {
    var h = heatmap.heatmap("v:pag", JUNE, "times", WorkTypeFilter.none(), c);
    int i = h.metrics().stream().map(HeatmapMetric::key).toList().indexOf("throughput");
    return h.rows().get(0).values().get(i);
  }

  private double individualThroughput(ContainerFilter c) {
    var d = individual.dashboard("p:ana", JUNE, WorkTypeFilter.none(), c);
    var pts =
        d.delivery().stream()
            .filter(s -> s.definition().key().equals("throughput"))
            .findFirst()
            .orElseThrow()
            .points();
    return pts.get(pts.size() - 1).value().value();
  }

  @Test
  void theFlowDashboardResponds() {
    assertThat(throughput(ContainerFilter.ALL)).isEqualTo(2.0);
    assertThat(throughput(ContainerFilter.LEAVES)).isEqualTo(1.0);
    assertThat(throughput(ContainerFilter.CONTAINERS)).isEqualTo(1.0);
  }

  @Test
  void theComparisonMatrixResponds() {
    assertThat(heatmapThroughput(ContainerFilter.ALL)).isEqualTo(2.0);
    assertThat(heatmapThroughput(ContainerFilter.LEAVES)).isEqualTo(1.0);
    assertThat(heatmapThroughput(ContainerFilter.CONTAINERS)).isEqualTo(1.0);
  }

  @Test
  void theIndividualPanelResponds() {
    assertThat(individualThroughput(ContainerFilter.ALL)).isEqualTo(2.0);
    assertThat(individualThroughput(ContainerFilter.LEAVES)).isEqualTo(1.0);
    assertThat(individualThroughput(ContainerFilter.CONTAINERS)).isEqualTo(1.0);
  }

  /** A distribuição segue a seleção: o padrão é folha, e "só contêineres" inverte o lado. */
  @Test
  void theIndividualDistributionFollowsTheSelection() {
    double leaves = hours(ContainerFilter.LEAVES);
    double all = hours(ContainerFilter.ALL);
    double containers = hours(ContainerFilter.CONTAINERS);

    assertThat(all).as("o padrão continua sendo o trabalho folha").isEqualTo(leaves);
    assertThat(leaves).isEqualTo(6.0);
    assertThat(containers).isEqualTo(10.0);
  }

  private double hours(ContainerFilter c) {
    return individual
        .dashboard("p:ana", JUNE, WorkTypeFilter.none(), c)
        .distribution()
        .types()
        .stream()
        .mapToDouble(WorkTypeSlice::hours)
        .sum();
  }

  private static RawEvent item(String id, String parent, double hours) {
    Map<String, String> d = new HashMap<>();
    d.put("type", "feature");
    d.put("ado_type", parent == null ? "User Story" : "Task");
    d.put("completed", "1");
    d.put("cycle_h", Double.toString(hours));
    d.put("hours", Double.toString(hours));
    long start = Instant.parse("2026-06-10T00:00:00Z").toEpochMilli();
    d.put("spans", start + ":" + (long) (start + hours * 3_600_000L));
    if (parent != null) {
      d.put("parent_event_id", parent);
    }
    return new RawEvent(
        id,
        EventType.WORKITEM,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        Map.copyOf(d));
  }
}
