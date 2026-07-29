package com.engperf.application.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.SyncStatePort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The stats read model attributes commits/PRs by committer identity and deploys by repository,
 * scopes to the requested node, and surfaces the unattributed remainder.
 */
class AdoStatsServiceTest {

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final FakeSyncState syncState = new FakeSyncState();
  private final AdoStatsService service = new AdoStatsService(structure, events, syncState);

  private int seq;

  @BeforeEach
  void seed() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", "ana@x.com", "t:checkout", LocalDate.of(2026, 1, 1)));
    structure.identities.add(new CommitterIdentity("ana@x.com", "Ana", "p:ana", 0));
    structure.repositories.add(new Repository("checkout-svc", "org", "P", "t:checkout", "prod"));

    events.add(commit("ana@x.com")); // attributed → Ana → Checkout → Pagamentos
    events.add(commit("ana@x.com"));
    events.add(deploy("checkout-svc")); // attributed by repo → Checkout (no person)
    events.add(commit("ghost@x.com")); // unmapped identity → unattributed
  }

  @Test
  void allNodeRollsUpToVerticalsPlusUnattributed() {
    AdoStats s = service.stats("all");

    assertThat(s.nodeLabel()).isEqualTo("Toda a estrutura");
    assertThat(s.totals().total()).isEqualTo(4);
    assertThat(s.totals().attributed()).isEqualTo(3);
    assertThat(s.totals().unattributed()).isEqualTo(1);
    assertThat(s.totals().byType().get(EventType.COMMIT)).isEqualTo(3);
    assertThat(s.totals().byType().get(EventType.DEPLOY)).isEqualTo(1);

    assertThat(s.rows())
        .extracting(AdoStats.Row::label, AdoStats.Row::rowType, AdoStats.Row::total)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Pagamentos", "vertical", 3L),
            org.assertj.core.groups.Tuple.tuple("Não atribuído", "unattributed", 1L));
  }

  @Test
  void teamNodeSplitsPeopleFromRepoOnlyDeploys() {
    AdoStats s = service.stats("t:checkout");

    assertThat(s.childType()).isEqualTo("person");
    assertThat(s.totals().total()).isEqualTo(3); // 2 commits + 1 deploy, not the ghost commit
    assertThat(s.rows())
        .extracting(AdoStats.Row::label, AdoStats.Row::rowType, AdoStats.Row::total)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Ana", "person", 2L),
            org.assertj.core.groups.Tuple.tuple("— sem pessoa (deploys/repo)", "repo", 1L));
  }

  @Test
  void reportsTheLastSyncSummary() {
    syncState.state =
        new SyncState(
            Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"), 4);
    AdoStats s = service.stats("all");
    assertThat(s.syncedCount()).isEqualTo(4);
    assertThat(s.lastSyncedAt()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z"));
  }

  private RawEvent commit(String identity) {
    return new RawEvent(
        "c" + (seq++),
        EventType.COMMIT,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        Map.of());
  }

  private RawEvent deploy(String repoKey) {
    return new RawEvent(
        "d" + (seq++),
        EventType.DEPLOY,
        Instant.parse("2026-06-11T10:00:00Z"),
        repoKey,
        null,
        1.0,
        null,
        false,
        Map.of());
  }

  private static final class FakeEvents implements EventStorePort {
    private final List<RawEvent> all = new ArrayList<>();

    void add(RawEvent e) {
      all.add(e);
    }

    @Override
    public void saveAll(Collection<RawEvent> batch) {
      all.addAll(batch);
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

  private static final class FakeSyncState implements SyncStatePort {
    private SyncState state;

    @Override
    public Optional<SyncState> load() {
      return Optional.ofNullable(state);
    }

    @Override
    public void save(SyncState s) {
      this.state = s;
    }
  }

  private static final class FakeStructure implements StructureRepositoryPort {
    final List<Vertical> verticals = new ArrayList<>();
    final List<Team> teams = new ArrayList<>();
    final List<Person> people = new ArrayList<>();
    final List<Repository> repositories = new ArrayList<>();
    final List<CommitterIdentity> identities = new ArrayList<>();

    @Override
    public List<Vertical> findVerticals() {
      return verticals;
    }

    @Override
    public List<Team> findTeams() {
      return teams;
    }

    @Override
    public List<Person> findPeople() {
      return people;
    }

    @Override
    public List<Repository> findRepositories() {
      return repositories;
    }

    @Override
    public List<CommitterIdentity> findIdentities() {
      return identities;
    }

    @Override
    public Vertical saveVertical(Vertical v) {
      return v;
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
    public Optional<CommitterIdentity> findIdentity(String identity) {
      return identities.stream().filter(i -> i.identity().equals(identity)).findFirst();
    }
  }
}
