package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComparisonHeatmapServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
  private static final List<String> COLUMN_ORDER =
      List.of(
          "deploy_freq",
          "lead_time",
          "cfr",
          "mttr",
          "cycle_time",
          "throughput",
          "flow_lead_time",
          "wip",
          "flow_efficiency",
          "pr_review_time",
          "pr_size",
          "ai_share",
          "ai_adoption",
          "ai_impact");

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService metrics = new MetricsService(structure, events, catalog, CLOCK);
  private final AiDashboardService ai = new AiDashboardService(metrics, catalog, structure);
  private final ComparisonHeatmapService heatmap =
      new ComparisonHeatmapService(metrics, catalog, structure, ai);

  private int seq = 0;

  private void baseStructure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.verticals.add(new Vertical("v:plat", "Plataforma", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.teams.add(new Team("t:core", "Core", "v:plat", null, null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.people.add(Person.create("p:bruno", "Bruno", null, "t:checkout", JAN1));
    structure.people.add(Person.create("p:carla", "Carla", null, "t:core", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    structure.identities.add(new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0));
    structure.identities.add(new CommitterIdentity("id-carla", "Carla", "p:carla", 0));
  }

  @Test
  void matrixIsChildrenByAllMetricsInCatalogOrder() {
    baseStructure();
    events.add(pr("id-ana"));
    events.add(pr("id-bruno"));
    events.add(pr("id-carla"));

    var h = heatmap.heatmap("all", Frequency.MONTHLY, "times");
    assertThat(h.metrics()).extracting(HeatmapMetric::key).containsExactlyElementsOf(COLUMN_ORDER);
    // Default overview scope compares the teams.
    assertThat(h.rows()).extracting(HeatmapRow::nodeId).containsExactly("t:checkout", "t:core");
    assertThat(h.rows()).allSatisfy(r -> assertThat(r.values()).hasSize(COLUMN_ORDER.size()));
    assertThat(h.rows()).extracting(HeatmapRow::rowType).containsOnly("Time");
  }

  @Test
  void aCellEqualsTheSameNodesDashboardCard() {
    baseStructure();
    events.add(doneItem("id-ana"));
    events.add(doneItem("id-ana"));
    events.add(doneItem("id-bruno"));

    var h = heatmap.heatmap("all", Frequency.MONTHLY, "times");
    int throughputCol = COLUMN_ORDER.indexOf("throughput");
    double cell =
        h.rows().stream()
            .filter(r -> r.nodeId().equals("t:checkout"))
            .findFirst()
            .orElseThrow()
            .values()
            .get(throughputCol);
    double card =
        metrics.cards("t:checkout", Frequency.MONTHLY).stream()
            .filter(c -> c.definition().key().equals("throughput"))
            .mapToDouble(c -> c.current().value())
            .findFirst()
            .orElseThrow();
    assertThat(cell).isEqualTo(card).isEqualTo(3); // Ana 2 + Bruno 1
  }

  @Test
  void rowsAreNodeAware() {
    baseStructure();

    assertThat(heatmap.heatmap("all", Frequency.MONTHLY, "verticais").rows())
        .extracting(HeatmapRow::nodeId)
        .containsExactly("v:pag", "v:plat");
    assertThat(heatmap.heatmap("all", Frequency.MONTHLY, "verticais").rows())
        .extracting(HeatmapRow::rowType)
        .containsOnly("Vertical");

    assertThat(heatmap.heatmap("v:pag", Frequency.MONTHLY, "times").rows())
        .extracting(HeatmapRow::nodeId)
        .containsExactly("t:checkout");

    var teamRows = heatmap.heatmap("t:checkout", Frequency.MONTHLY, "times").rows();
    assertThat(teamRows).extracting(HeatmapRow::nodeId).containsExactly("p:ana", "p:bruno");
    assertThat(teamRows).extracting(HeatmapRow::rowType).containsOnly("Pessoa");
  }

  private RawEvent pr(String identity) {
    return new RawEvent(
        "e" + (seq++),
        EventType.PR,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        4.0,
        "review",
        false,
        Map.of("cycle_h", "8", "num", "6", "den", "8"));
  }

  /** A completed work item — feeds throughput/cycle_time/flow on the WORKITEM channel. */
  private RawEvent doneItem(String identity) {
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        Map.of("completed", "1", "type", "feature", "cycle_h", "8", "num", "6", "den", "8"));
  }

  private static final class FakeEvents implements EventStorePort {
    private final List<RawEvent> all = new ArrayList<>();

    @Override
    public void saveAll(java.util.Collection<RawEvent> events) {
      all.addAll(events);
    }

    void add(RawEvent e) {
      all.add(e);
    }

    @Override
    public List<RawEvent> findByTypeBetween(EventType type, Instant from, Instant to) {
      return all.stream()
          .filter(e -> e.type() == type)
          .filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
          .toList();
    }

    @Override
    public long count() {
      return all.size();
    }
  }

  private static final class FakeStructure implements StructureRepositoryPort {
    final List<Vertical> verticals = new ArrayList<>();
    final List<Team> teams = new ArrayList<>();
    final List<Person> people = new ArrayList<>();
    final List<Repository> repositories = new ArrayList<>();
    final List<CommitterIdentity> identities = new ArrayList<>();

    @Override
    public Vertical saveVertical(Vertical v) {
      return v;
    }

    @Override
    public List<Vertical> findVerticals() {
      return verticals;
    }

    @Override
    public Optional<Vertical> findVertical(String id) {
      return verticals.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    @Override
    public void deleteVertical(String id) {}

    @Override
    public Team saveTeam(Team t) {
      return t;
    }

    @Override
    public List<Team> findTeams() {
      return teams;
    }

    @Override
    public Optional<Team> findTeam(String id) {
      return teams.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    @Override
    public void deleteTeam(String id) {}

    @Override
    public Person savePerson(Person p) {
      return p;
    }

    @Override
    public List<Person> findPeople() {
      return people;
    }

    @Override
    public Optional<Person> findPerson(String id) {
      return people.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public void deletePerson(String id) {}

    @Override
    public Repository saveRepository(Repository r) {
      return r;
    }

    @Override
    public List<Repository> findRepositories() {
      return repositories;
    }

    @Override
    public Optional<Repository> findRepository(String key) {
      return repositories.stream().filter(r -> r.key().equals(key)).findFirst();
    }

    @Override
    public void deleteRepository(String key) {}

    @Override
    public CommitterIdentity saveIdentity(CommitterIdentity c) {
      return c;
    }

    @Override
    public List<CommitterIdentity> findIdentities() {
      return identities;
    }

    @Override
    public Optional<CommitterIdentity> findIdentity(String identity) {
      return identities.stream().filter(c -> c.identity().equals(identity)).findFirst();
    }
  }
}
