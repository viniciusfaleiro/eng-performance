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
import com.engperf.domain.structure.Repository;
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

/** Reading metrics by work type: population, coverage, untyped events and what never filters. */
class WorkTypeFilterMetricsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricsService service =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);

  private int seq;

  @BeforeEach
  void structure() {
    structure.verticals.add(new Vertical("v:eng", "Eng", "p:ana"));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:eng", "p:ana", null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    structure.identities.add(new CommitterIdentity("id-ghost", "Ghost", null, 0));
    structure.repositories.add(new Repository("r:web", "org", "Proj", "t:checkout", null));
  }

  @Test
  @DisplayName("a mediana é recalculada sobre a população filtrada, não composta por tipo")
  void medianIsRecomputedOverTheFilteredPopulation() {
    events.add(item("id-ana", WorkType.BUG, 2.0));
    events.add(item("id-ana", WorkType.BUG, 4.0));
    events.add(item("id-ana", WorkType.FEATURE, 40.0));
    events.add(item("id-ana", WorkType.FEATURE, 60.0));

    assertThat(card("cycle_time", WorkTypeFilter.of(WorkType.BUG))).isEqualTo(3.0);
    assertThat(card("cycle_time", WorkTypeFilter.of(WorkType.FEATURE))).isEqualTo(50.0);
    assertThat(card("cycle_time", WorkTypeFilter.none())).isEqualTo(22.0);
  }

  @Test
  @DisplayName("vários tipos selecionados agregam a união como uma população só")
  void severalTypesAggregateAsOnePopulation() {
    events.add(item("id-ana", WorkType.BUG, 1.0));
    events.add(item("id-ana", WorkType.TECH_DEBT, 3.0));
    events.add(item("id-ana", WorkType.FEATURE, 99.0));

    assertThat(card("throughput", WorkTypeFilter.of(WorkType.BUG, WorkType.TECH_DEBT)))
        .isEqualTo(2.0);
    assertThat(card("cycle_time", WorkTypeFilter.of(WorkType.BUG, WorkType.TECH_DEBT)))
        .isEqualTo(2.0);
  }

  @Test
  @DisplayName("seleção vazia devolve exatamente o que a leitura devolvia antes do filtro")
  void emptySelectionChangesNothing() {
    events.add(item("id-ana", WorkType.BUG, 2.0));
    events.add(item("id-ana", WorkType.FEATURE, 8.0));

    MetricCard filtered = cardOf("cycle_time", WorkTypeFilter.none());
    MetricCard plain =
        service.cards("t:checkout", period()).stream()
            .filter(c -> c.definition().key().equals("cycle_time"))
            .findFirst()
            .orElseThrow();
    assertThat(filtered.current().value()).isEqualTo(plain.current().value());
    assertThat(filtered.coverage()).isEqualTo(plain.coverage());
    assertThat(filtered.filtered()).isFalse();
    assertThat(filtered.untypedExcluded()).isZero();
  }

  @Test
  @DisplayName("o filtro compõe com a coorte de IA em vez de substituí-la")
  void filterComposesWithTheAiCohort() {
    events.add(pr("id-ana", 10.0, true, "bug"));
    events.add(pr("id-ana", 20.0, true, "feature"));
    events.add(pr("id-ana", 30.0, false, "bug"));

    MetricSeries aiBugs =
        service.cohortSeries(
            "code_cycle_time", "t:checkout", period(), true, WorkTypeFilter.of(WorkType.BUG));
    assertThat(last(aiBugs)).isEqualTo(10.0);

    MetricSeries nonAiBugs =
        service.cohortSeries(
            "code_cycle_time", "t:checkout", period(), false, WorkTypeFilter.of(WorkType.BUG));
    assertThat(last(nonAiBugs)).isEqualTo(30.0);
  }

  @Test
  @DisplayName("cobertura sob filtro mede o denominador filtrado")
  void coverageMeasuresTheFilteredDenominator() {
    events.add(item("id-ana", WorkType.BUG, 2.0));
    events.add(item("id-ghost", WorkType.BUG, 2.0)); // atribuível a ninguém
    events.add(item("id-ghost", WorkType.FEATURE, 2.0)); // outro tipo: fora da pergunta

    MetricCard bugs = cardOf("cycle_time", WorkTypeFilter.of(WorkType.BUG));
    assertThat(bugs.coverage().total()).isEqualTo(2);
    assertThat(bugs.coverage().attributed()).isEqualTo(1);
    assertThat(bugs.filtered()).isTrue();
  }

  @Test
  @DisplayName("evento sem tipo sai da população e é contado à parte, não como falta de atribuição")
  void untypedEventsAreCountedApart() {
    events.add(pr("id-ana", 10.0, false, "bug"));
    events.add(pr("id-ana", 20.0, false, null)); // PR sem vínculo com work item

    MetricCard bugs = cardOf("pr_review_time", WorkTypeFilter.of(WorkType.BUG));
    assertThat(bugs.current().value()).isEqualTo(10.0);
    assertThat(bugs.untypedExcluded()).isEqualTo(1);
    assertThat(bugs.coverage().percent()).isEqualTo(100.0);
  }

  @Test
  @DisplayName("métrica de deploy ignora o filtro e declara que não foi filtrada")
  void deployMetricsIgnoreTheFilter() {
    events.add(deploy());
    events.add(deploy());

    MetricCard freq = cardOf("deploy_freq", WorkTypeFilter.of(WorkType.BUG));
    assertThat(freq.current().value()).isEqualTo(2.0);
    assertThat(freq.filtered()).isFalse();
    assertThat(freq.untypedExcluded()).isZero();
  }

  @Test
  @DisplayName("o detalhamento de itens herda o filtro do card que o abriu")
  void drilldownInheritsTheFilter() {
    events.add(item("id-ana", WorkType.BUG, 2.0));
    events.add(item("id-ana", WorkType.FEATURE, 9.0));

    assertThat(service.items("cycle_time", "t:checkout", period(), WorkTypeFilter.of(WorkType.BUG)))
        .hasSize(1);
    assertThat(service.items("cycle_time", "t:checkout", period(), WorkTypeFilter.none()))
        .hasSize(2);
  }

  // ---- helpers ----

  private static Period period() {
    return Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK));
  }

  private double card(String key, WorkTypeFilter filter) {
    return cardOf(key, filter).current().value();
  }

  private MetricCard cardOf(String key, WorkTypeFilter filter) {
    return service.cards("t:checkout", period(), filter).stream()
        .filter(c -> c.definition().key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private static double last(MetricSeries s) {
    return s.points().get(s.points().size() - 1).value().value();
  }

  private RawEvent item(String identity, WorkType type, double cycleHours) {
    Map<String, String> d = new HashMap<>();
    d.put("completed", "1");
    d.put("type", type.key());
    d.put("cycle_h", Double.toString(cycleHours));
    d.put("lead_h", Double.toString(cycleHours + 1));
    d.put("num", "1.0");
    d.put("den", "2.0");
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse("2026-06-15T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        d);
  }

  private RawEvent pr(String identity, double hours, boolean ai, String workTypes) {
    Map<String, String> d = new HashMap<>();
    d.put("cycle_h", Double.toString(hours));
    if (workTypes != null) {
      d.put("work_types", workTypes);
    }
    return new RawEvent(
        "pr" + (seq++),
        EventType.PR,
        Instant.parse("2026-06-15T10:00:00Z"),
        null,
        identity,
        hours,
        "review",
        ai,
        d);
  }

  private RawEvent deploy() {
    return new RawEvent(
        "d" + (seq++),
        EventType.DEPLOY,
        Instant.parse("2026-06-15T10:00:00Z"),
        "r:web",
        null,
        3.0,
        null,
        false,
        Map.of("num", "0", "den", "1"));
  }
}
