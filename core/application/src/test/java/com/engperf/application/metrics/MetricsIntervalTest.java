package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Metrics over a freely chosen interval, and over a rolling window.
 *
 * <p>Separate from {@link MetricsServiceTest} because the questions are different: that one asks
 * whether a calendar bucket rolls up correctly, this one asks what changes — and what must not —
 * when the period stops being a bucket.
 */
class MetricsIntervalTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);

  /** 20/04 a 09/06 — começa e termina no meio de um mês, e atravessa um mês inteiro. */
  private static final Period CHOSEN =
      Period.between(
          Frequency.MONTHLY, LocalDate.parse("2026-04-20"), LocalDate.parse("2026-06-09"));

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricsService service =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);

  private int seq;

  @BeforeEach
  void setUp() {
    structure.verticals.add(new Vertical("v:eng", "Eng", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:eng", null, null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
  }

  /**
   * The card has to answer for the interval that was selected. The series' last point is the
   * interval's last <em>slice</em> — the nine days of June in a range ending on the 9th — so a card
   * fed from it would silently report a fraction of what was asked for.
   */
  @Test
  void aChosenIntervalCardCoversTheWholeIntervalNotItsLastSlice() {
    fourInsideAndTwoOutside();

    assertThat(throughput(CHOSEN).current().value()).isEqualTo(4);
    assertThat(service.items("throughput", "p:ana", CHOSEN)).hasSize(4);
  }

  /** The frequency stops picking the bucket and starts cutting the interval into chart points. */
  @Test
  void theSeriesCutsAChosenIntervalByFrequencyClippedToItsEdges() {
    fourInsideAndTwoOutside();

    var monthly = service.series("throughput", "p:ana", CHOSEN).points();
    assertThat(monthly)
        .extracting(SeriesPoint::bucketStart)
        .containsExactly("2026-04-20", "2026-05-01", "2026-06-01");
    // A primeira fatia começa no dia 20, não no dia 1: o item do dia 15 fica fora do gráfico
    // exatamente como fica fora do card.
    assertThat(monthly).extracting(p -> p.value().value()).containsExactly(1.0, 2.0, 1.0);

    var weekly = service.series("throughput", "p:ana", CHOSEN.at(Frequency.WEEKLY)).points();
    assertThat(weekly).hasSizeGreaterThan(monthly.size());
    assertThat(weekly.get(0).bucketStart()).isEqualTo("2026-04-20");
    assertThat(weekly.stream().mapToDouble(p -> p.value().value()).sum())
        .as("trocar a granularidade redistribui os mesmos itens, não muda quantos são")
        .isEqualTo(4.0);
  }

  /**
   * Comparing 51 days against "the previous month" would mix durations and make the percentage
   * meaningless, so the baseline is the 51 days immediately before.
   */
  @Test
  void aChosenIntervalComparesAgainstTheSameNumberOfDaysBeforeIt() {
    events.add(doneItemOn("2026-04-25"));
    events.add(doneItemOn("2026-05-10"));
    events.add(doneItemOn("2026-05-20"));
    events.add(doneItemOn("2026-06-05")); // 4 dentro do intervalo
    events.add(doneItemOn("2026-03-01"));
    events.add(doneItemOn("2026-03-15")); // 2 dentro dos 51 dias anteriores
    // 27/02 cai um dia antes do intervalo anterior: é o vizinho que prova onde a borda está.
    events.add(doneItemOn("2026-02-27"));

    assertThat(CHOSEN.days()).isEqualTo(51);
    assertThat(CHOSEN.previous().start()).isEqualTo(LocalDate.parse("2026-02-28"));
    assertThat(throughput(CHOSEN).current().changePct()).isEqualTo(100.0);
  }

  /**
   * A median over an interval is a median over its events, not an average of its slices' medians —
   * the same rule that forbids averaging team medians, applied along time instead of structure.
   */
  @Test
  void aMedianOverAnIntervalIsRecomputedNotAveragedAcrossItsSlices() {
    events.add(prOn(100, "2026-04-25")); // abril: mediana 100
    events.add(prOn(1, "2026-05-10"));
    events.add(prOn(1, "2026-05-15"));
    events.add(prOn(1, "2026-05-20")); // maio: mediana 1
    events.add(prOn(100, "2026-06-05")); // junho: mediana 100

    // Mediana de {100, 1, 1, 1, 100} = 1. A média das medianas mensais daria 67.
    assertThat(card(CHOSEN, "pr_review_time").current().value()).isEqualTo(1);
  }

  /** A rolling window reaches the engine as an ordinary interval and needs no rule of its own. */
  @Test
  void aRollingWindowIsMeasuredInFullAgainstThePrecedingWindow() {
    // Relógio em 2026-06-30; últimos 7 dias = 24/06 a 30/06, anteriores = 17/06 a 23/06.
    events.add(doneItemOn("2026-06-25"));
    events.add(doneItemOn("2026-06-26"));
    events.add(doneItemOn("2026-06-20")); // janela anterior

    Period last7 = Period.lastDays(Frequency.WEEKLY, 7, LocalDate.now(CLOCK));

    assertThat(last7.inProgress(LocalDate.now(CLOCK)))
        .as("uma janela cheia não tem fatia decorrida a recortar")
        .isFalse();
    assertThat(throughput(last7).current().value()).isEqualTo(2);
    assertThat(throughput(last7).current().changePct()).isEqualTo(100.0);
  }

  private void fourInsideAndTwoOutside() {
    events.add(doneItemOn("2026-04-15")); // antes do início — fora
    events.add(doneItemOn("2026-04-25"));
    events.add(doneItemOn("2026-05-10"));
    events.add(doneItemOn("2026-05-20"));
    events.add(doneItemOn("2026-06-05"));
    events.add(doneItemOn("2026-06-20")); // depois do fim — fora
  }

  private MetricCard throughput(Period period) {
    return card(period, "throughput");
  }

  private MetricCard card(Period period, String key) {
    return service.cards("p:ana", period).stream()
        .filter(c -> c.definition().key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private RawEvent doneItemOn(String date) {
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse(date + "T10:00:00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        Map.of("completed", "1", "type", "feature", "cycle_h", "5.0", "lead_h", "8.0"));
  }

  private RawEvent prOn(double hours, String date) {
    return new RawEvent(
        "e" + (seq++),
        EventType.PR,
        Instant.parse(date + "T10:00:00Z"),
        null,
        "id-ana",
        hours,
        null,
        false,
        null);
  }
}
