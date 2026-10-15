package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkType;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The composed dashboards under a work-type selection: what filters, what does not, and why. */
class DashboardsByWorkTypeTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
  private static final WorkTypeFilter BUGS = WorkTypeFilter.of(WorkType.BUG);

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

  private int seq;

  @BeforeEach
  void baseStructure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
  }

  @Test
  @DisplayName("Fluxo: os cards de board filtram e declaram o filtro; o scatter segue junto")
  void flowCardsAndScatterFollowTheSelection() {
    events.add(item(WorkType.BUG, 4));
    events.add(item(WorkType.FEATURE, 40));
    events.add(item(WorkType.FEATURE, 60));

    Map<String, FlowCard> bugs = flowCards("v:pag", BUGS);
    assertThat(bugs.get("cycle_time").value().value()).isEqualTo(4.0);
    assertThat(bugs.get("throughput").value().value()).isEqualTo(1.0);
    assertThat(bugs.get("cycle_time").filtered()).isTrue();

    FlowDashboard dash = flow.dashboard("v:pag", period(), BUGS);
    assertThat(dash.scatter())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.nodeId()).isEqualTo("t:checkout");
              assertThat(s.throughput()).isEqualTo(1.0);
              assertThat(s.cycleTime()).isEqualTo(4.0);
            });
  }

  @Test
  @DisplayName("Fluxo: as fases descrevem a mesma população filtrada do Cycle Time")
  void phasesMatchTheFilteredCycleTime() {
    events.add(item(WorkType.BUG, 4));
    events.add(item(WorkType.FEATURE, 40));

    FlowDashboard dash = flow.dashboard("t:checkout", period(), BUGS);
    assertThat(dash.phases())
        .filteredOn(p -> p.key().equals("active_time"))
        .singleElement()
        .satisfies(p -> assertThat(p.hours()).isEqualTo(2.0));
  }

  @Test
  @DisplayName("IA: as duas coortes são recortadas pelo mesmo filtro, e o PR sem vínculo é contado")
  void aiCohortsAreRestrictedSymmetricallyAndUnlinkedPrsAreCounted() {
    events.add(pr(10, true, "bug"));
    events.add(pr(30, false, "bug"));
    events.add(pr(1, true, "feature"));
    events.add(pr(2, false, null)); // sem vínculo com o board

    AiDashboard dash = ai.dashboard("t:checkout", period(), BUGS);
    // 30h sem IA contra 10h com IA → 66,7% mais rápido, só sobre bugs.
    AiCard impact =
        dash.cards().stream()
            .filter(c -> c.definition().key().equals("ai_impact"))
            .findFirst()
            .orElseThrow();
    assertThat(impact.value().value()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.01));
    assertThat(impact.filtered()).isTrue();
    assertThat(impact.untypedExcluded()).isEqualTo(1);
    assertThat(dash.cycleWithAi().get(dash.cycleWithAi().size() - 1)).isEqualTo(10.0);
    assertThat(dash.cycleWithoutAi().get(dash.cycleWithoutAi().size() - 1)).isEqualTo(30.0);
  }

  @Test
  @DisplayName("Comparativo: as colunas de deploy ficam marcadas como não filtradas")
  void heatmapMarksTheColumnsTheFilterDoesNotReach() {
    events.add(item(WorkType.BUG, 4));

    ComparisonHeatmap h = heatmap.heatmap("v:pag", period(), "times", BUGS);
    assertThat(h.metrics())
        .filteredOn(m -> !m.filterable())
        .extracting(HeatmapMetric::key)
        .containsExactly("deploy_freq", "lead_time", "cfr", "mttr");
    assertThat(h.metrics()).filteredOn(HeatmapMetric::filterable).isNotEmpty();
  }

  @Test
  @DisplayName("Individual: a entrega filtra, mas a distribuição por tipo continua mostrando tudo")
  void theIndividualPanelFiltersDeliveryButNotTheDistribution() {
    events.add(item(WorkType.BUG, 4));
    events.add(item(WorkType.FEATURE, 40));

    IndividualDashboard panel = individual.dashboard("p:ana", period(), BUGS);
    MetricSeries throughput =
        panel.delivery().stream()
            .filter(s -> s.definition().key().equals("throughput"))
            .findFirst()
            .orElseThrow();
    assertThat(throughput.points().get(throughput.points().size() - 1).value().value())
        .isEqualTo(1.0);
    assertThat(panel.filtered()).isTrue();

    // A feature continua na distribuição mesmo com o filtro em bug: a seção responde "como o
    // período se dividiu", e filtrá-la seria sempre uma fatia só a 100%.
    assertThat(panel.distribution().types()).hasSize(5);
    assertThat(panel.distribution().types())
        .filteredOn(s -> s.itemCount() > 0)
        .extracting(WorkTypeSlice::type)
        .containsExactlyInAnyOrder("feature", "bug");
  }

  @Test
  @DisplayName(
      "filtro ativo sem nenhum evento do tipo devolve vazio explícito, não um número velho")
  void anEmptySelectionResultIsExplicit() {
    events.add(item(WorkType.FEATURE, 40));

    Map<String, FlowCard> debt = flowCards("t:checkout", WorkTypeFilter.of(WorkType.TECH_DEBT));
    assertThat(debt.get("throughput").value().value()).isZero();
    assertThat(debt.get("cycle_time").value().value()).isZero();
    assertThat(debt.get("cycle_time").coverage().total()).isZero();
  }

  // ---- helpers ----

  private Map<String, FlowCard> flowCards(String node, WorkTypeFilter filter) {
    Map<String, FlowCard> byKey = new HashMap<>();
    flow.dashboard(node, period(), filter).cards().forEach(c -> byKey.put(c.definition().key(), c));
    return byKey;
  }

  private static Period period() {
    return Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK));
  }

  /**
   * A completed work item of a type: half the cycle active, a quarter waiting, a quarter review.
   */
  private RawEvent item(WorkType type, double cycleHours) {
    Map<String, String> d = new HashMap<>();
    d.put("completed", "1");
    d.put("type", type.key());
    d.put("cycle_h", Double.toString(cycleHours));
    d.put("lead_h", Double.toString(cycleHours + 1));
    d.put("active_h", Double.toString(cycleHours / 2));
    d.put("wait_h", Double.toString(cycleHours / 4));
    d.put("review_h", Double.toString(cycleHours / 4));
    d.put("num", Double.toString(cycleHours / 2));
    d.put("den", Double.toString(cycleHours));
    d.put("hours", Double.toString(cycleHours / 2));
    d.put("summary", "item " + seq);
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse("2026-06-15T10:00:00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        d);
  }

  private RawEvent pr(double hours, boolean ai, String workTypes) {
    Map<String, String> d = new HashMap<>();
    d.put("cycle_h", Double.toString(hours));
    d.put("first_pass", "1");
    if (workTypes != null) {
      d.put("work_types", workTypes);
    }
    return new RawEvent(
        "pr" + (seq++),
        EventType.PR,
        Instant.parse("2026-06-15T10:00:00Z"),
        null,
        "id-ana",
        hours,
        "review",
        ai,
        d);
  }
}
