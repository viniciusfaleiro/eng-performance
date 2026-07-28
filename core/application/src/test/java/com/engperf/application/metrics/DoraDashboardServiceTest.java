package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.Tier;
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

class DoraDashboardServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService metrics = new MetricsService(structure, events, catalog, CLOCK);
  private final DoraDashboardService dora =
      new DoraDashboardService(metrics, catalog, structure, CLOCK);

  private int seq = 0;

  private void structure2Verticals() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.verticals.add(new Vertical("v:plat", "Plataforma", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.teams.add(new Team("t:core", "Core", "v:plat", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1)));
    structure.repositories.add(new Repository("r:checkout", "P", "t:checkout"));
    structure.repositories.add(new Repository("r:core", "P", "t:core"));
    structure.repositories.add(new Repository("r:orphan", "P", null));
  }

  @Test
  void dashboardHasFourDoraCardsWithTiers() {
    structure2Verticals();
    // r:checkout: 20 deploys in June, fast lead (10h), 2 failed, recoveries 0.5h → elite-ish.
    for (int i = 0; i < 20; i++) {
      events.add(deploy("r:checkout", "success", 10, null));
    }
    events.add(deploy("r:checkout", "failed", 10, null));
    events.add(deploy("r:checkout", "recovery", 10, 0.5));
    events.add(deploy("r:checkout", "failed", 10, null));
    events.add(deploy("r:checkout", "recovery", 10, 0.5));

    var dash = dora.dashboard("t:checkout", Frequency.MONTHLY);
    assertThat(dash.cards())
        .extracting(c -> c.definition().key())
        .containsExactly("deploy_freq", "lead_time", "cfr", "mttr");

    var byKey = index(dash);
    assertThat(byKey.get("lead_time").tier()).isEqualTo(Tier.ELITE); // 10h < 24h
    assertThat(byKey.get("mttr").tier()).isEqualTo(Tier.ELITE); // 0.5h < 1h
    // CFR = 2 failed / 24 total ≈ 8.3% → Elite (≤15%).
    assertThat(byKey.get("cfr").value().value())
        .isCloseTo(2.0 / 24.0, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(byKey.get("cfr").tier()).isEqualTo(Tier.ELITE);
    // deploy_freq value = 24 deploys in the month.
    assertThat(byKey.get("deploy_freq").value().value()).isEqualTo(24);
  }

  @Test
  void mttrIsMedianOfRecoveryHoursOnly() {
    structure2Verticals();
    events.add(deploy("r:checkout", "success", 5, null)); // no recovery_hours → excluded from MTTR
    events.add(deploy("r:checkout", "recovery", 5, 2.0));
    events.add(deploy("r:checkout", "recovery", 5, 6.0));
    events.add(deploy("r:checkout", "recovery", 5, 4.0));
    var byKey = index(dora.dashboard("t:checkout", Frequency.MONTHLY));
    assertThat(byKey.get("mttr").value().value()).isEqualTo(4.0); // median(2,6,4)
  }

  @Test
  void cfrCountsRecoveryAsNonFailure() {
    structure2Verticals();
    events.add(deploy("r:checkout", "failed", 5, null));
    events.add(deploy("r:checkout", "recovery", 5, 3.0));
    events.add(deploy("r:checkout", "success", 5, null));
    // 1 failed / 3 deploys.
    var byKey = index(dora.dashboard("t:checkout", Frequency.MONTHLY));
    assertThat(byKey.get("cfr").value().value())
        .isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void rankingRanksVerticalsAtOverviewAndNeverPeople() {
    structure2Verticals();
    // Pagamentos deploys more than Plataforma → ranks first (deploy_freq higher-better).
    for (int i = 0; i < 10; i++) {
      events.add(deploy("r:checkout", "success", 5, null));
    }
    for (int i = 0; i < 3; i++) {
      events.add(deploy("r:core", "success", 5, null));
    }
    var dash = dora.dashboard("all", Frequency.MONTHLY);
    assertThat(dash.childType()).isEqualTo("vertical");
    assertThat(dash.ranking()).extracting(RankingRow::nodeId).containsExactly("v:pag", "v:plat");
    // No ranking row is a person.
    assertThat(dash.ranking()).noneMatch(r -> r.nodeId().startsWith("p:"));
  }

  @Test
  void teamNodeHasNoRanking() {
    structure2Verticals();
    var dash = dora.dashboard("t:checkout", Frequency.MONTHLY);
    assertThat(dash.childType()).isNull();
    assertThat(dash.ranking()).isEmpty();
  }

  private static Map<String, DoraCard> index(DoraDashboard dash) {
    var m = new java.util.HashMap<String, DoraCard>();
    dash.cards().forEach(c -> m.put(c.definition().key(), c));
    return m;
  }

  private RawEvent deploy(String repo, String outcome, double leadHours, Double recoveryHours) {
    var detail = new java.util.HashMap<String, String>();
    detail.put("outcome", outcome);
    detail.put("num", "failed".equals(outcome) ? "1" : "0");
    detail.put("den", "1");
    if (recoveryHours != null) {
      detail.put("recovery_hours", Double.toString(recoveryHours));
    }
    return new RawEvent(
        "e" + (seq++),
        EventType.DEPLOY,
        Instant.parse("2026-06-10T10:00:00Z"),
        repo,
        null,
        leadHours,
        null,
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
