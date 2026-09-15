package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
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
import java.util.NoSuchElementException;
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
    return service.cards(node, period(Frequency.MONTHLY)).stream()
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

  /** O período corrente do relógio fixo do teste. */
  private static Period period(Frequency f) {

    return Period.of(f, LocalDate.now(CLOCK));
  }

  @Test
  void rollsUpPerNodeWithPopulationMedianAndManagerWithoutCommits() {
    baseStructure();
    // PRs feed pr_review_time (median review hours). Completed work items feed throughput.
    // Ana: 2 PRs @10h ; Bruno: 8 PRs @2h ; Diego(pay): 4 PRs @5h ; Carla(pay manager): none.
    for (int i = 0; i < 2; i++) {
      events.add(pr("id-ana", 10));
      events.add(doneItem("id-ana"));
    }
    for (int i = 0; i < 8; i++) {
      events.add(pr("id-bruno", 2));
      events.add(doneItem("id-bruno"));
    }
    for (int i = 0; i < 4; i++) {
      events.add(pr("id-diego", 5));
      events.add(doneItem("id-diego"));
    }

    // Throughput = completed work items, SUM per person, rolling up to team/vertical/all.
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

    var series = service.series("deploy_freq", "all", period(Frequency.MONTHLY));
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

    events.add(doneItemOn("id-bruno", "2026-03-10")); // while in Checkout
    events.add(doneItemOn("id-bruno", "2026-06-10")); // after moving to Pay

    // Monthly series: March item counts for Checkout, not Pay; June item counts for Pay.
    var checkout = service.series("throughput", "t:checkout", period(Frequency.MONTHLY));
    var pay = service.series("throughput", "t:pay", period(Frequency.MONTHLY));
    assertThat(pointValue(checkout, "2026-03-01")).isEqualTo(1);
    assertThat(pointValue(checkout, "2026-06-01")).isEqualTo(0);
    assertThat(pointValue(pay, "2026-06-01")).isEqualTo(1);
    assertThat(pointValue(pay, "2026-03-01")).isEqualTo(0);
  }

  @Test
  void unknownMetricIsRejected() {
    baseStructure();
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> service.series("nope", "all", period(Frequency.MONTHLY)));
  }

  @Test
  void itemsWithNoBucketDefaultsToTheCurrentPeriod() {
    baseStructure();
    events.add(doneItem("id-ana")); // 2026-06-15 — the fixed clock's current month
    events.add(doneItemOn("id-ana", "2026-05-15")); // previous month — must not appear by default

    var items = service.items("throughput", "p:ana", period(Frequency.MONTHLY));
    assertThat(items).hasSize(1);
    assertThat(items.get(0).occurredAt()).isEqualTo(Instant.parse("2026-06-15T10:00:00Z"));
  }

  @Test
  void itemsFromAChosenPeriodMatchThatPeriod() {
    baseStructure();
    events.add(doneItem("id-ana")); // 2026-06-15
    events.add(doneItemOn("id-ana", "2026-05-15"));

    var items =
        service.items(
            "throughput", "p:ana", Period.of(Frequency.MONTHLY, LocalDate.parse("2026-05-01")));
    assertThat(items).hasSize(1);
    assertThat(items.get(0).occurredAt()).isEqualTo(Instant.parse("2026-05-15T10:00:00Z"));
  }

  @Test
  void itemsRejectsUnknownMetric() {
    baseStructure();
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> service.items("nope", "all", period(Frequency.MONTHLY)));
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

  /** A completed work item on {@code date} — feeds throughput (count), cycle_time and flow. */
  private RawEvent doneItemOn(String identity, String date) {
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse(date + "T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        java.util.Map.of(
            "completed",
            "1",
            "type",
            "feature",
            "cycle_h",
            "5.0",
            "lead_h",
            "8.0",
            "num",
            "4.0",
            "den",
            "5.0"));
  }

  private RawEvent doneItem(String identity) {
    return doneItemOn(identity, "2026-06-15");
  }

  /**
   * A fatia decorrida existe para não comparar 10 dias contra um mês inteiro. Ela vale para o
   * período em curso e só para ele: um mês encerrado não tem recorte a fazer.
   */
  @Test
  void onlyTheRunningPeriodComparesAnElapsedSlice() {
    baseStructure();
    // Relógio no dia 10 → junho está em curso, com 10 dias decorridos.
    MetricsService midMonth =
        new MetricsService(
            structure,
            events,
            new MetricCatalog(),
            Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC));
    events.add(doneItemOn("id-ana", "2026-06-05")); // junho, dentro dos 10 dias
    events.add(doneItemOn("id-ana", "2026-05-03")); // maio, dentro dos 10 primeiros dias
    events.add(doneItemOn("id-ana", "2026-05-20")); // maio, fora dos 10 primeiros
    events.add(doneItemOn("id-ana", "2026-05-25"));
    events.add(doneItemOn("id-ana", "2026-04-02")); // abril: 2 itens, um deles nos 10 primeiros
    events.add(doneItemOn("id-ana", "2026-04-22"));

    var june = throughputOf(midMonth, Period.of(Frequency.MONTHLY, LocalDate.parse("2026-06-01")));
    var may = throughputOf(midMonth, Period.of(Frequency.MONTHLY, LocalDate.parse("2026-05-01")));

    // Junho em curso: 1 item contra o 1 item dos 10 primeiros dias de maio — não contra os 3.
    assertThat(june.current().value()).isEqualTo(1);
    assertThat(june.current().changePct()).isZero();
    // Maio encerrado: 3 itens contra os 2 de abril **inteiro**. Com a fatia decorrida aplicada por
    // engano, a comparação seria 1 dia de maio contra 1 dia de abril e a evolução sumiria.
    assertThat(may.current().value()).isEqualTo(3);
    assertThat(may.current().changePct()).isEqualTo(50.0);
  }

  private static MetricCard throughputOf(MetricsService svc, Period period) {
    return svc.cards("p:ana", period).stream()
        .filter(c -> c.definition().key().equals("throughput"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aPeriodBeforeAnyIngestionAnswersZeroInsteadOfFailing() {
    baseStructure();
    events.add(doneItem("id-ana"));

    Period longPast = Period.of(Frequency.MONTHLY, LocalDate.parse("2019-03-01"));

    assertThat(service.cards("p:ana", longPast))
        .as("um mês sem eventos é uma resposta legítima: zero")
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.current().value()).isZero());
  }

  /** Card, série e drilldown do mesmo período têm de descrever o mesmo intervalo. */
  @Test
  void cardSeriesAndDrilldownAgreeOnTheChosenPeriod() {
    baseStructure();
    events.add(doneItemOn("id-ana", "2026-05-20"));
    events.add(doneItemOn("id-ana", "2026-06-15"));
    Period may = Period.of(Frequency.MONTHLY, LocalDate.parse("2026-05-01"));

    double cardValue =
        service.cards("p:ana", may).stream()
            .filter(c -> c.definition().key().equals("throughput"))
            .findFirst()
            .orElseThrow()
            .current()
            .value();
    var points = service.series("throughput", "p:ana", may).points();
    double lastPoint = points.get(points.size() - 1).value().value();
    var items = service.items("throughput", "p:ana", may);

    assertThat(cardValue).isEqualTo(1);
    assertThat(lastPoint).as("a série termina no período escolhido").isEqualTo(cardValue);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).occurredAt()).isEqualTo(Instant.parse("2026-05-20T10:00:00Z"));
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
}
