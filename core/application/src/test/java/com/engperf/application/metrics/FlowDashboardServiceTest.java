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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class FlowDashboardServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService metrics = new MetricsService(structure, events, catalog, CLOCK);
  private final FlowDashboardService flow = new FlowDashboardService(metrics, catalog, structure);

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

  private Map<String, FlowCard> cards(String node) {
    var m = new java.util.HashMap<String, FlowCard>();
    flow.dashboard(node, Frequency.MONTHLY).cards().forEach(c -> m.put(c.definition().key(), c));
    return m;
  }

  @Test
  void cardsPhasesAndFlowEfficiency() {
    baseStructure();
    // Ana: two PRs with phases (coding, pickup, review, deploy).
    events.add(pr("id-ana", 4, 1, 2, 1, 200)); // cycle 8, active 6
    events.add(pr("id-ana", 6, 1, 2, 1, 400)); // cycle 10, active 8

    var dash = flow.dashboard("t:checkout", Frequency.MONTHLY);
    assertThat(dash.cards())
        .extracting(c -> c.definition().key())
        .containsExactly(
            "cycle_time", "throughput", "wip", "pr_review_time", "pr_size", "flow_efficiency");

    var byKey = cards("t:checkout");
    assertThat(byKey.get("cycle_time").value().value()).isEqualTo(9.0); // median(8,10)
    assertThat(byKey.get("throughput").value().value()).isEqualTo(2);
    assertThat(byKey.get("pr_size").value().value()).isEqualTo(300.0); // median(200,400)
    // Flow efficiency = Σactive / Σcycle = (6+8)/(8+10) = 14/18.
    assertThat(byKey.get("flow_efficiency").value().value())
        .isCloseTo(14.0 / 18.0, Offset.offset(1e-9));

    // Phase breakdown = median of each phase over the population.
    var phases = new java.util.HashMap<String, Double>();
    dash.phases().forEach(p -> phases.put(p.key(), p.hours()));
    assertThat(phases.get("coding_time")).isEqualTo(5.0); // median(4,6)
    assertThat(phases.get("pickup_time")).isEqualTo(1.0);
    assertThat(phases.get("pr_review_time")).isEqualTo(2.0);
    assertThat(phases.get("deploy_time")).isEqualTo(1.0);
  }

  @Test
  void scatterRanksVerticalsAtOverviewNeverPeople() {
    baseStructure();
    events.add(pr("id-ana", 4, 1, 2, 1, 100));
    events.add(pr("id-bruno", 4, 1, 2, 1, 100));
    events.add(pr("id-carla", 4, 1, 2, 1, 100));

    var dash = flow.dashboard("all", Frequency.MONTHLY);
    assertThat(dash.childType()).isEqualTo("vertical");
    assertThat(dash.scatter()).extracting(ScatterPoint::nodeId).containsExactly("v:pag", "v:plat");
    assertThat(dash.scatter()).noneMatch(s -> s.nodeId().startsWith("p:"));
    // Pagamentos has 2 PRs (Ana+Bruno), Plataforma 1 (Carla).
    var pag =
        dash.scatter().stream().filter(s -> s.nodeId().equals("v:pag")).findFirst().orElseThrow();
    assertThat(pag.throughput()).isEqualTo(2);
    assertThat(pag.cycleTime()).isEqualTo(8.0); // median(8,8)
  }

  @Test
  void teamNodeHasNoScatter() {
    baseStructure();
    var dash = flow.dashboard("t:checkout", Frequency.MONTHLY);
    assertThat(dash.childType()).isNull();
    assertThat(dash.scatter()).isEmpty();
  }

  private RawEvent pr(
      String identity, double coding, double pickup, double review, double deploy, double lines) {
    double cycle = coding + pickup + review + deploy;
    double active = coding + review;
    Map<String, String> detail =
        Map.of(
            "coding_h", Double.toString(coding),
            "pickup_h", Double.toString(pickup),
            "review_h", Double.toString(review),
            "deploy_h", Double.toString(deploy),
            "cycle_h", Double.toString(cycle),
            "lines", Double.toString(lines),
            "num", Double.toString(active),
            "den", Double.toString(cycle));
    // numericValue = review hours (pr_review_time reads the default measure).
    return new RawEvent(
        "e" + (seq++),
        EventType.PR,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        review,
        "review",
        false,
        detail);
  }

  private static final class FakeEvents implements EventStorePort {
    private final List<RawEvent> all = new ArrayList<>();

    void add(RawEvent e) {
      all.add(e);
    }

    @Override
    public void saveAll(java.util.Collection<RawEvent> events) {
      all.addAll(events);
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
