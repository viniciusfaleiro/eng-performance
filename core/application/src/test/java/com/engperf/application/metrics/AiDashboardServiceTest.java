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

class AiDashboardServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService metrics = new MetricsService(structure, events, catalog, CLOCK);
  private final AiDashboardService ai = new AiDashboardService(metrics, catalog, structure);

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

  private Map<String, AiCard> cards(String node) {
    var m = new java.util.HashMap<String, AiCard>();
    ai.dashboard(node, Frequency.MONTHLY).cards().forEach(c -> m.put(c.definition().key(), c));
    return m;
  }

  @Test
  void shareAdoptionAndImpact() {
    baseStructure();
    // Commits: Ana 1 AI + 1 non-AI; Bruno 2 non-AI → share 1/4, adoption (Ana only) 1/2.
    events.add(commit("id-ana", true));
    events.add(commit("id-ana", false));
    events.add(commit("id-bruno", false));
    events.add(commit("id-bruno", false));
    // PRs: Ana two AI PRs @cycle 6; Bruno two non-AI PRs @cycle 10 → impact (10-6)/10 = 40%.
    events.add(pr("id-ana", 6, true));
    events.add(pr("id-ana", 6, true));
    events.add(pr("id-bruno", 10, false));
    events.add(pr("id-bruno", 10, false));

    var byKey = cards("t:checkout");
    assertThat(byKey.get("ai_share").value().value()).isCloseTo(0.25, Offset.offset(1e-9));
    assertThat(byKey.get("ai_adoption").value().value()).isCloseTo(0.5, Offset.offset(1e-9));
    assertThat(byKey.get("ai_impact").value().value()).isCloseTo(40.0, Offset.offset(1e-9));
    // Coverage of impact = AI share of PRs = 2/4.
    assertThat(byKey.get("ai_impact").coverage().percent()).isEqualTo(50.0);
  }

  @Test
  void adoptionCountsEachPersonOnce() {
    baseStructure();
    // Ana: 3 AI commits (still one adopter); Bruno: 2 non-AI commits.
    events.add(commit("id-ana", true));
    events.add(commit("id-ana", true));
    events.add(commit("id-ana", true));
    events.add(commit("id-bruno", false));
    events.add(commit("id-bruno", false));

    var byKey = cards("t:checkout");
    // Adoption = adopters(Ana)=1 / active(Ana,Bruno)=2, not 3/5.
    assertThat(byKey.get("ai_adoption").value().value()).isCloseTo(0.5, Offset.offset(1e-9));
    assertThat(byKey.get("ai_share").value().value()).isCloseTo(0.6, Offset.offset(1e-9));
  }

  @Test
  void adoptionRankingComparesVerticalsNeverPeople() {
    baseStructure();
    events.add(commit("id-ana", true)); // v:pag adopter
    events.add(commit("id-bruno", false)); // v:pag active, not adopter
    events.add(commit("id-carla", true)); // v:plat adopter (only person)

    var dash = ai.dashboard("all", Frequency.MONTHLY);
    assertThat(dash.childType()).isEqualTo("vertical");
    // v:plat adoption 1/1 > v:pag adoption 1/2 → v:plat first.
    assertThat(dash.adoption()).extracting(AdoptionRank::nodeId).containsExactly("v:plat", "v:pag");
    assertThat(dash.adoption()).noneMatch(r -> r.nodeId().startsWith("p:"));
    assertThat(dash.adoption().get(0).adoption()).isCloseTo(1.0, Offset.offset(1e-9));
    assertThat(dash.adoption().get(1).adoption()).isCloseTo(0.5, Offset.offset(1e-9));
  }

  @Test
  void oneCohortEmptyYieldsNoImpact() {
    baseStructure();
    // Only non-AI PRs → the AI cohort is empty → no comparison (value 0, no evolution).
    events.add(pr("id-bruno", 10, false));
    events.add(pr("id-bruno", 12, false));

    var impact = cards("t:checkout").get("ai_impact");
    assertThat(impact.value().value()).isEqualTo(0.0);
    assertThat(impact.value().changePct()).isNull();
  }

  @Test
  void impactCardMatchesTheDashboard() {
    baseStructure();
    events.add(pr("id-ana", 6, true));
    events.add(pr("id-ana", 6, true));
    events.add(pr("id-bruno", 10, false));
    events.add(pr("id-bruno", 10, false));

    var fromDashboard = cards("t:checkout").get("ai_impact");
    var standalone = ai.impact("t:checkout", Frequency.MONTHLY);
    assertThat(standalone.value().value()).isEqualTo(fromDashboard.value().value());
    assertThat(standalone.coverage().percent()).isEqualTo(fromDashboard.coverage().percent());
  }

  @Test
  void cohortSeriesPartitionThePopulation() {
    baseStructure();
    events.add(pr("id-ana", 6, true));
    events.add(pr("id-ana", 6, true));
    events.add(pr("id-bruno", 10, false));
    events.add(pr("id-bruno", 10, false));
    events.add(pr("id-bruno", 10, false));

    double aiPrs = last(metrics.cohortSeries("throughput", "t:checkout", Frequency.MONTHLY, true));
    double nonPrs =
        last(metrics.cohortSeries("throughput", "t:checkout", Frequency.MONTHLY, false));
    double all =
        metrics.cards("t:checkout", Frequency.MONTHLY).stream()
            .filter(c -> c.definition().key().equals("throughput"))
            .mapToDouble(c -> c.current().value())
            .findFirst()
            .orElseThrow();
    assertThat(aiPrs).isEqualTo(2);
    assertThat(nonPrs).isEqualTo(3);
    assertThat(aiPrs + nonPrs).isEqualTo(all); // cohorts partition, none counted twice
  }

  private static double last(MetricSeries s) {
    return s.points().get(s.points().size() - 1).value().value();
  }

  private RawEvent commit(String identity, boolean ai) {
    return new RawEvent(
        "c" + (seq++),
        EventType.COMMIT,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        ai,
        null);
  }

  private RawEvent pr(String identity, double cycle, boolean ai) {
    return new RawEvent(
        "e" + (seq++),
        EventType.PR,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        cycle,
        "review",
        ai,
        Map.of("cycle_h", Double.toString(cycle)));
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
