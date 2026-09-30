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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WIP conta quem esteve em progresso dentro do período, e não quem tem a data do registro nele.
 *
 * <p>O relógio está fixado em 30/06/2026 12:00 UTC.
 */
class WipByIntervalTest {

  private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricsService metrics =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);

  @BeforeEach
  void structure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    // Entrou no time antes do item mais antigo do fixture: a atribuição continua sendo as-of-event,
    // pela data do registro, e um item cujo registro precede a entrada da pessoa no time não
    // atribui a ela. Vale saber que, para uma métrica de intervalo, essa data é a da última
    // alteração e não a do começo do trabalho — está registrado como questão aberta na change.
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2025, 1, 1)));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
  }

  private double wip(Frequency f) {
    return metrics.cards("p:ana", Period.of(f, LocalDate.now(CLOCK))).stream()
        .filter(c -> c.definition().key().equals("wip"))
        .findFirst()
        .orElseThrow()
        .current()
        .value();
  }

  private double wipOf(Period p) {
    return metrics.cards("p:ana", p).stream()
        .filter(c -> c.definition().key().equals("wip"))
        .findFirst()
        .orElseThrow()
        .current()
        .value();
  }

  /**
   * O caso que motivou a mudança: em progresso desde 10/08/2025, aberta, ninguém mexeu. A data do
   * registro é de dez meses atrás — pela regra antiga ela não era nem buscada.
   */
  @Test
  void anItemOpenAndUntouchedForMonthsCountsToday() {
    events.add(open("wi:1", "2025-08-10T09:00:00Z"));

    assertThat(wip(Frequency.DAILY)).isEqualTo(1.0);
    assertThat(wip(Frequency.WEEKLY)).isEqualTo(1.0);
    assertThat(wip(Frequency.MONTHLY)).isEqualTo(1.0);
  }

  /** A data do evento vira agosto por causa da conclusão; junho tem de continuar contando. */
  @Test
  void anItemCompletedLaterStillCountsInThePeriodItWasWorked() {
    events.add(
        closed("wi:1", "2026-06-05T09:00:00Z", "2026-06-12T18:00:00Z", "2026-08-20T10:00:00Z"));

    assertThat(wipOf(Period.of(Frequency.MONTHLY, LocalDate.of(2026, 6, 15)))).isEqualTo(1.0);
    assertThat(wipOf(Period.of(Frequency.MONTHLY, LocalDate.of(2026, 5, 15)))).isZero();
  }

  /** Uma tarefa em progresso no dia 3 e concluída no dia 10 esteve em progresso naquele mês. */
  @Test
  void anItemCompletedInsideThePeriodCounts() {
    events.add(
        closed("wi:1", "2026-06-03T09:00:00Z", "2026-06-10T17:00:00Z", "2026-06-10T17:00:00Z"));

    assertThat(wip(Frequency.MONTHLY)).isEqualTo(1.0);
  }

  /** Quem nunca saiu do backlog não tem intervalo, e não é trabalho em progresso. */
  @Test
  void anItemThatNeverStartedDoesNotCount() {
    Map<String, String> detail = new HashMap<>();
    detail.put("type", "feature");
    events.add(
        new RawEvent(
            "wi:9",
            EventType.WORKITEM,
            Instant.parse("2026-06-15T10:00:00Z"),
            null,
            "id-ana",
            null,
            null,
            false,
            Map.copyOf(detail)));

    assertThat(wip(Frequency.MONTHLY)).isZero();
  }

  /** O buraco entre dois intervalos não é progresso. */
  @Test
  void aPeriodInsideAGapCountsNothing() {
    events.add(
        gapped(
            "wi:1",
            "2026-06-01T00:00:00Z",
            "2026-06-03T00:00:00Z",
            "2026-06-20T00:00:00Z",
            "2026-06-22T00:00:00Z"));

    assertThat(
            wipOf(
                Period.between(
                    Frequency.DAILY, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 15))))
        .isZero();
    assertThat(wipOf(Period.of(Frequency.MONTHLY, LocalDate.of(2026, 6, 15)))).isEqualTo(1.0);
  }

  /**
   * O defeito que fazia o passado se mexer: o mesmo mês fechado, lido antes e depois de o item
   * concluir, dava valores diferentes porque a data do evento pulava para a conclusão.
   */
  @Test
  void aClosedPeriodDoesNotMoveWhenTheItemLaterCompletes() {
    Period june = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 6, 15));

    events.add(open("wi:1", "2026-06-05T09:00:00Z"));
    double whileOpen = wipOf(june);

    events.clear();
    events.add(
        closed("wi:1", "2026-06-05T09:00:00Z", "2026-06-12T18:00:00Z", "2026-08-20T10:00:00Z"));

    assertThat(wipOf(june)).isEqualTo(whileOpen).isEqualTo(1.0);
  }

  /** A métrica não é aditiva: o mesmo item conta em cada período que atravessa. */
  @Test
  void anIntervalMetricIsNotAdditiveAcrossPeriods() {
    events.add(
        closed("wi:1", "2026-06-22T09:00:00Z", "2026-06-24T17:00:00Z", "2026-06-24T17:00:00Z"));

    // Segunda 22, terça 23 e quarta 24 — o item conta em cada dia e uma vez na semana.
    for (int d = 22; d <= 24; d++) {
      assertThat(wipOf(Period.of(Frequency.DAILY, LocalDate.of(2026, 6, d)))).isEqualTo(1.0);
    }
    assertThat(wipOf(Period.of(Frequency.WEEKLY, LocalDate.of(2026, 6, 23)))).isEqualTo(1.0);
  }

  /** O item aberto vai até a leitura: contar itens simultâneos continua sendo contagem. */
  @Test
  void concurrentItemsAreCountedNotSummed() {
    events.add(open("wi:1", "2026-06-01T09:00:00Z"));
    events.add(open("wi:2", "2026-06-02T09:00:00Z"));
    events.add(open("wi:3", "2026-06-03T09:00:00Z"));

    assertThat(wip(Frequency.MONTHLY)).isEqualTo(3.0);
  }

  // ---- fixture ----

  private static RawEvent open(String id, String workStart) {
    long a = Instant.parse(workStart).toEpochMilli();
    // Fecha o span no relógio da ingestão, como a ingestão faz; quem estica é a leitura.
    long b = Instant.parse("2026-06-28T09:00:00Z").toEpochMilli();
    long end = Math.max(b, a + 3_600_000L);
    Map<String, String> d = base();
    d.put("spans", a + ":" + end);
    d.put("in_progress", "1");
    return wi(id, Instant.parse(workStart), d);
  }

  private static RawEvent closed(String id, String workStart, String workEnd, String recordedAt) {
    Map<String, String> d = base();
    d.put(
        "spans",
        Instant.parse(workStart).toEpochMilli() + ":" + Instant.parse(workEnd).toEpochMilli());
    d.put("completed", "1");
    return wi(id, Instant.parse(recordedAt), d);
  }

  private static RawEvent gapped(String id, String a1, String b1, String a2, String b2) {
    Map<String, String> d = base();
    d.put(
        "spans",
        Instant.parse(a1).toEpochMilli()
            + ":"
            + Instant.parse(b1).toEpochMilli()
            + ","
            + Instant.parse(a2).toEpochMilli()
            + ":"
            + Instant.parse(b2).toEpochMilli());
    d.put("completed", "1");
    return wi(id, Instant.parse(b2), d);
  }

  private static Map<String, String> base() {
    Map<String, String> d = new HashMap<>();
    d.put("type", "feature");
    d.put("ado_type", "Task");
    return d;
  }

  private static RawEvent wi(String id, Instant at, Map<String, String> detail) {
    return new RawEvent(
        id, EventType.WORKITEM, at, null, "id-ana", null, null, false, Map.copyOf(detail));
  }
}
