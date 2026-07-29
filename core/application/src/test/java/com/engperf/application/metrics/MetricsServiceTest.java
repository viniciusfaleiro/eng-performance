package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricsService service =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);

  private double card(String node, String key) {
    return service.cards(node, Frequency.MONTHLY).stream()
        .filter(c -> c.definition().key().equals(key))
        .map(c -> c.current().value())
        .findFirst()
        .orElseThrow();
  }

  private void baseStructure() {
    structure.verticals.add(new Vertical("v:eng", "Eng", "p:ana"));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:eng", "p:ana", null));
    structure.teams.add(new Team("t:pay", "Pay", "v:eng", "p:carla", null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.people.add(Person.create("p:bruno", "Bruno", null, "t:checkout", JAN1));
    structure.people.add(
        Person.create("p:carla", "Carla", null, "t:pay", JAN1)); // manager, sem PRs
    structure.people.add(Person.create("p:diego", "Diego", null, "t:pay", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    structure.identities.add(new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0));
    structure.identities.add(new CommitterIdentity("id-diego", "Diego", "p:diego", 0));
    structure.identities.add(new CommitterIdentity("id-ghost", "Ghost", null, 0)); // unlinked
    structure.repositories.add(new Repository("r:web", "org", "Proj", "t:checkout", null));
    structure.repositories.add(new Repository("r:orphan", "org", "Proj", null, null)); // unmapped
  }

  @Test
  void rollsUpPerNodeWithPopulationMedianAndManagerWithoutCommits() {
    baseStructure();
    // Ana: 2 PRs @10h ; Bruno: 8 PRs @2h ; Diego(pay): 4 PRs @5h ; Carla(pay manager): none.
    for (int i = 0; i < 2; i++) {
      events.add(pr("id-ana", 10));
    }
    for (int i = 0; i < 8; i++) {
      events.add(pr("id-bruno", 2));
    }
    for (int i = 0; i < 4; i++) {
      events.add(pr("id-diego", 5));
    }

    // Throughput (SUM, person) rolls up to team/vertical/all.
    assertThat(card("p:ana", "throughput")).isEqualTo(2);
    assertThat(card("p:bruno", "throughput")).isEqualTo(8);
    assertThat(card("t:checkout", "throughput")).isEqualTo(10);
    assertThat(card("v:eng", "throughput")).isEqualTo(14); // checkout 10 + pay 4
    assertThat(card("all", "throughput")).isEqualTo(14);

    // Manager (Carla) has no commits: her card is 0, but her team aggregates from Diego.
    assertThat(card("p:carla", "throughput")).isEqualTo(0);
    assertThat(card("t:pay", "throughput")).isEqualTo(4);

    // Median over the TEAM population (ten PRs), not the average of per-person medians.
    assertThat(card("p:ana", "pr_review_time")).isEqualTo(10);
    assertThat(card("t:checkout", "pr_review_time")).isEqualTo(2);
  }

  @Test
  void ratioAndCoverageReflectUnattributedEvents() {
    baseStructure();
    // Deploys on the mapped repo: 3 total, 1 failed → CFR = 1/3.
    events.add(deploy("r:web", 1, 4)); // failed=1
    events.add(deploy("r:web", 0, 6));
    events.add(deploy("r:web", 0, 5));
    // A deploy on an unmapped repo → unattributed (drops coverage, excluded from the team).
    events.add(deploy("r:orphan", 0, 9));

    assertThat(card("t:checkout", "cfr"))
        .isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(card("t:checkout", "deploy_freq")).isEqualTo(3); // orphan excluded

    var series = service.series("deploy_freq", "all", Frequency.MONTHLY);
    assertThat(series.coverage().total()).isEqualTo(4);
    assertThat(series.coverage().attributed()).isEqualTo(3);
    assertThat(series.coverage().percent()).isLessThan(100.0);
  }

  @Test
  void deployRepoKeyMatchesTeamCaseInsensitively() {
    baseStructure();
    // Repo registered in Title Case; ADO returns the source repo lower-cased on the deploy event.
    structure.repositories.add(new Repository("Asa-Core-Card", "org", "Proj", "t:checkout", null));
    events.add(deploy("asa-core-card", 0, 5));

    assertThat(card("t:checkout", "deploy_freq")).isEqualTo(1); // matched despite the case mismatch
  }

  @Test
  void attributionIsAsOfEvent() {
    structure.verticals.add(new Vertical("v:eng", "Eng", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:eng", null, null));
    structure.teams.add(new Team("t:pay", "Pay", "v:eng", null, null));
    // Bruno started in Checkout, moved to Pay on 2026-05-01.
    Person bruno =
        Person.create("p:bruno", "Bruno", null, "t:checkout", JAN1)
            .moveToTeam("t:pay", LocalDate.of(2026, 5, 1));
    structure.people.add(bruno);
    structure.identities.add(new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0));

    events.add(prOn("id-bruno", 1, "2026-03-10")); // while in Checkout
    events.add(prOn("id-bruno", 1, "2026-06-10")); // after moving to Pay

    // Monthly series: March PR counts for Checkout, not Pay; June PR counts for Pay.
    var checkout = service.series("throughput", "t:checkout", Frequency.MONTHLY);
    var pay = service.series("throughput", "t:pay", Frequency.MONTHLY);
    assertThat(pointValue(checkout, "2026-03-01")).isEqualTo(1);
    assertThat(pointValue(checkout, "2026-06-01")).isEqualTo(0);
    assertThat(pointValue(pay, "2026-06-01")).isEqualTo(1);
    assertThat(pointValue(pay, "2026-03-01")).isEqualTo(0);
  }

  @Test
  void unknownMetricIsRejected() {
    baseStructure();
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> service.series("nope", "all", Frequency.MONTHLY));
  }

  private static double pointValue(MetricSeries s, String bucketStart) {
    return s.points().stream()
        .filter(p -> p.bucketStart().equals(bucketStart))
        .map(p -> p.value().value())
        .findFirst()
        .orElseThrow();
  }

  private int seq = 0;

  private RawEvent pr(String identity, double hours) {
    return prOn(identity, hours, "2026-06-10");
  }

  private RawEvent prOn(String identity, double hours, String date) {
    return new RawEvent(
        "e" + (seq++),
        EventType.PR,
        Instant.parse(date + "T10:00:00Z"),
        null,
        identity,
        hours,
        null,
        false,
        null);
  }

  private RawEvent deploy(String repo, double failed, double leadHours) {
    // One deploy feeds three metrics: deploy_freq (count), lead_time (median of
    // numericValue=hours),
    // cfr (ratio of detail num/den). numericValue = lead hours; detail.num = failed flag.
    return new RawEvent(
        "e" + (seq++),
        EventType.DEPLOY,
        Instant.parse("2026-06-10T10:00:00Z"),
        repo,
        null,
        leadHours,
        null,
        false,
        java.util.Map.of("num", Double.toString(failed), "den", "1"));
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
