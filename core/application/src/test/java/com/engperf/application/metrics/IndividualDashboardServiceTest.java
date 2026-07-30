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

class IndividualDashboardServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricsService metrics =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);
  private final IndividualDashboardService individual =
      new IndividualDashboardService(structure, events, metrics, CLOCK);

  private int seq = 0;

  private void baseStructure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.people.add(Person.create("p:bruno", "Bruno", null, "t:checkout", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    structure.identities.add(new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0));
  }

  @Test
  void calendarCountsCommitsPerDay() {
    baseStructure();
    events.add(commit("id-ana", "2026-06-10"));
    events.add(commit("id-ana", "2026-06-10"));
    events.add(commit("id-ana", "2026-06-11"));
    events.add(commit("id-bruno", "2026-06-10")); // not Ana → excluded

    var dash = individual.dashboard("p:ana", Frequency.MONTHLY);
    assertThat(dash.calendar()).hasSize(371);
    assertThat(dash.calendar().stream().mapToInt(CalendarDay::count).sum()).isEqualTo(3);
    assertThat(day(dash, "2026-06-10")).isEqualTo(2);
    assertThat(day(dash, "2026-06-11")).isEqualTo(1);
  }

  @Test
  void assertivenessIsFirstPassOverTotal() {
    baseStructure();
    events.add(pr("id-ana", "2026-06-10", true));
    events.add(pr("id-ana", "2026-06-11", true));
    events.add(pr("id-ana", "2026-06-12", false));

    var dash = individual.dashboard("p:ana", Frequency.MONTHLY);
    assertThat(dash.assertivenessPct()).isCloseTo(200.0 / 3.0, Offset.offset(1e-9));
  }

  @Test
  void reviewsGivenAndReceivedUseDifferentSides() {
    baseStructure();
    // Ana reviews Bruno's PRs (given), Bruno reviews Ana's PR (received by Ana).
    events.add(review("id-ana", "id-bruno", "2026-06-10", true, 3));
    events.add(review("id-ana", "id-bruno", "2026-06-11", false, 5));
    events.add(review("id-bruno", "id-ana", "2026-06-10", true, 2));

    var r = individual.dashboard("p:ana", Frequency.MONTHLY).reviews();
    assertThat(r.reviewsGiven()).isEqualTo(2);
    assertThat(r.reviewsReceived()).isEqualTo(1);
    assertThat(r.commentsGiven()).isEqualTo(8);
    assertThat(r.approvalsGiven()).isEqualTo(1);
    assertThat(r.rejectionsGiven()).isEqualTo(1);
  }

  @Test
  void workTypesReportHoursAndShare() {
    baseStructure();
    events.add(workItem("id-ana", "2026-06-10", "feature", 6));
    events.add(workItem("id-ana", "2026-06-11", "feature", 4));
    events.add(workItem("id-ana", "2026-06-12", "bug", 10));

    var types = individual.dashboard("p:ana", Frequency.MONTHLY).workTypes();
    assertThat(types)
        .extracting(WorkTypeSlice::type)
        .containsExactly("feature", "bug", "tech_debt", "maintenance", "docs");
    var feature = types.stream().filter(t -> t.type().equals("feature")).findFirst().orElseThrow();
    assertThat(feature.hours()).isEqualTo(10.0);
    assertThat(feature.sharePct()).isCloseTo(50.0, Offset.offset(1e-9)); // 10 of 20h
  }

  @Test
  void workTypeHoursClipToPeriodAndProrateAcrossConcurrentItems() {
    baseStructure();
    // Two items open in parallel the whole year, both changed in the current period (2026-06-30).
    events.add(
        workItemSpan(
            "id-ana", "2026-06-30", "feature", "2026-01-01T00:00:00Z", "2026-12-31T00:00:00Z"));
    events.add(
        workItemSpan(
            "id-ana", "2026-06-30", "bug", "2026-01-01T00:00:00Z", "2026-12-31T00:00:00Z"));

    var monthly = individual.dashboard("p:ana", Frequency.MONTHLY).workTypes();
    double total = monthly.stream().mapToDouble(WorkTypeSlice::hours).sum();
    // June's ~720h are SHARED by the two concurrent items (360 each) — not 720 each nor ~8736 life.
    assertThat(total).isEqualTo(720.0);
    assertThat(typeHours(monthly, "feature")).isEqualTo(360.0);
    assertThat(typeHours(monthly, "bug")).isEqualTo(360.0);
  }

  private static double typeHours(List<WorkTypeSlice> ws, String type) {
    return ws.stream().filter(t -> t.type().equals(type)).findFirst().get().hours();
  }

  private RawEvent workItemSpan(
      String identity, String changedDate, String type, String spanFrom, String spanTo) {
    long a = Instant.parse(spanFrom).toEpochMilli();
    long b = Instant.parse(spanTo).toEpochMilli();
    double h = (b - a) / 3_600_000.0;
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        at(changedDate),
        null,
        identity,
        h,
        null,
        false,
        Map.of("type", type, "hours", Double.toString(h), "spans", a + ":" + b));
  }

  @Test
  void activityCarriesTheAdoLink() {
    baseStructure();
    events.add(pr("id-ana", "2026-06-10", true));

    var activity = individual.dashboard("p:ana", Frequency.MONTHLY).activity();
    assertThat(activity).isNotEmpty();
    assertThat(activity.get(0).url()).contains("dev.azure.com");
    assertThat(activity.get(0).kind()).isEqualTo("pr");
  }

  @Test
  void flagsCommitsWithoutAiPrOrWorkItem() {
    baseStructure();
    events.add(commit("id-ana", "2026-06-10")); // ai=false, no PR, no work item

    var flags = individual.dashboard("p:ana", Frequency.MONTHLY).conventions();
    assertThat(flags)
        .extracting(ConventionFlag::reference)
        .contains("Convenção 16 · Assistência de IA");
    assertThat(flags).anyMatch(f -> f.reference().startsWith("Convenção 10"));
    assertThat(flags).anyMatch(f -> f.reference().startsWith("Convenção 20"));
    assertThat(flags).allMatch(f -> f.severity().equals("warn"));
    assertThat(flags).extracting(ConventionFlag::code).contains("16", "10", "20");
  }

  @Test
  void flagsPrsWithoutReviewReceived() {
    baseStructure();
    events.add(aiCommit("id-ana", "2026-06-10")); // AI present, so no IA flag
    events.add(pr("id-ana", "2026-06-10", true)); // authored PR, but nobody reviewed it

    var flags = individual.dashboard("p:ana", Frequency.MONTHLY).conventions();
    assertThat(flags).anyMatch(f -> f.reference().startsWith("Convenção 13"));
    assertThat(flags).noneMatch(f -> f.reference().startsWith("Convenção 16"));
  }

  @Test
  void flagsNoActivityAsUnmappedIdentity() {
    baseStructure(); // Ana has an identity but no events at all

    var flags = individual.dashboard("p:ana", Frequency.MONTHLY).conventions();
    assertThat(flags).hasSize(1);
    assertThat(flags.get(0).reference()).startsWith("Convenções 1");
  }

  @Test
  void cleanContributorHasNoFlags() {
    baseStructure();
    events.add(aiCommit("id-ana", "2026-06-10"));
    events.add(pr("id-ana", "2026-06-10", true));
    events.add(workItem("id-ana", "2026-06-11", "feature", 6));
    events.add(review("id-bruno", "id-ana", "2026-06-10", true, 2)); // Ana's PR got reviewed

    var flags = individual.dashboard("p:ana", Frequency.MONTHLY).conventions();
    assertThat(flags).isEmpty();
  }

  private static int day(IndividualDashboard d, String date) {
    return d.calendar().stream()
        .filter(c -> c.date().equals(date))
        .mapToInt(CalendarDay::count)
        .findFirst()
        .orElseThrow();
  }

  private RawEvent commit(String identity, String date) {
    return new RawEvent(
        "c" + (seq++), EventType.COMMIT, at(date), null, identity, null, null, false, Map.of());
  }

  private RawEvent aiCommit(String identity, String date) {
    return new RawEvent(
        "c" + (seq++), EventType.COMMIT, at(date), null, identity, null, null, true, Map.of());
  }

  private RawEvent pr(String identity, String date, boolean firstPass) {
    return new RawEvent(
        "e" + (seq++),
        EventType.PR,
        at(date),
        null,
        identity,
        4.0,
        "review",
        false,
        Map.of(
            "cycle_h", "8",
            "first_pass", firstPass ? "1" : "0",
            "url", "https://dev.azure.com/minhaorg/x/_git/x/pullrequest/1",
            "summary", "feat: x",
            "repo", "x"));
  }

  private RawEvent review(
      String reviewer, String author, String date, boolean approved, int comments) {
    return new RawEvent(
        "r" + (seq++),
        EventType.REVIEW,
        at(date),
        null,
        reviewer,
        null,
        null,
        false,
        Map.of(
            "decision",
            approved ? "approved" : "changes_requested",
            "comments",
            Integer.toString(comments),
            "author",
            author));
  }

  private RawEvent workItem(String identity, String date, String type, double hours) {
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        at(date),
        null,
        identity,
        5.0,
        null,
        false,
        Map.of("type", type, "hours", Double.toString(hours)));
  }

  private static Instant at(String date) {
    return Instant.parse(date + "T10:00:00Z");
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
