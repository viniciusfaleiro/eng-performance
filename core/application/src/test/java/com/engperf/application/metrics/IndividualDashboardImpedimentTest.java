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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * O que o tempo impedido faz com as horas do liderado.
 *
 * <p>A distribuição responde para onde o trabalho da pessoa foi, e item parado não é trabalho indo
 * a lugar nenhum — então o tempo impedido sai das horas. O que <em>não</em> muda é o {@code spans}:
 * é ele que responde "o que estava em andamento?" para o WIP, e um item bloqueado continua em
 * andamento.
 */
class IndividualDashboardImpedimentTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final FakeAccounts accounts = new FakeAccounts();
  private final MetricsService metrics =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);
  private final IndividualDashboardService individual =
      new IndividualDashboardService(structure, events, metrics, accounts, CLOCK);

  private int seq = 0;

  @BeforeEach
  void baseStructure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1)));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
  }

  private List<WorkTypeSlice> types() {
    return individual
        .dashboard("p:ana", Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK)))
        .distribution()
        .types();
  }

  private static double typeHours(List<WorkTypeSlice> ws, String type) {
    return ws.stream().filter(t -> t.type().equals(type)).findFirst().orElseThrow().hours();
  }

  private static Instant at(String date) {
    return Instant.parse(date + "T12:00:00Z");
  }

  /**
   * Tempo impedido não é trabalho do liderado: sai das horas, e o que fica é a parte do intervalo
   * em que o item realmente andou. O {@code spans} continua inteiro — é ele que responde "o que
   * estava em andamento?" para o WIP, e um item bloqueado continua em andamento.
   */
  @Test
  void impededTimeDoesNotCountAsTheirWork() {
    events.add(
        impededSpan(
            "id-ana",
            "2026-06-30",
            "feature",
            "2026-06-10T00:00:00Z",
            "2026-06-10T10:00:00Z",
            "2026-06-10T02:00:00Z",
            "2026-06-10T06:00:00Z"));

    var types = types();

    assertThat(typeHours(types, "feature")).isEqualTo(6.0); // 10h em andamento, 4h travadas
  }

  @Test
  void anItemWithoutImpedimentIsUnchanged() {
    events.add(
        workItemSpan(
            "id-ana", "2026-06-30", "feature", "2026-06-10T00:00:00Z", "2026-06-10T10:00:00Z"));

    var types = types();

    assertThat(typeHours(types, "feature")).isEqualTo(10.0);
  }

  /** Impedimento no meio do intervalo o parte em dois — as duas pontas continuam contando. */
  @Test
  void impedimentInTheMiddleLeavesBothEnds() {
    events.add(
        impededSpan(
            "id-ana",
            "2026-06-30",
            "bug",
            "2026-06-10T00:00:00Z",
            "2026-06-10T12:00:00Z",
            "2026-06-10T04:00:00Z",
            "2026-06-10T08:00:00Z"));

    var types = types();

    assertThat(typeHours(types, "bug")).isEqualTo(8.0); // 0→4 e 8→12
  }

  private RawEvent impededSpan(
      String identity,
      String changedDate,
      String type,
      String spanFrom,
      String spanTo,
      String impededFrom,
      String impededTo) {
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
        Map.of(
            "type",
            type,
            "hours",
            Double.toString(h),
            "spans",
            a + ":" + b,
            "impeded_spans",
            Instant.parse(impededFrom).toEpochMilli()
                + ":"
                + Instant.parse(impededTo).toEpochMilli()));
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
}
