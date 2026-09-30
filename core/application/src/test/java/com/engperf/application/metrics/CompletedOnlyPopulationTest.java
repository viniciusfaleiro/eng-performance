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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Flow Efficiency e as fases descrevem a mesma população do Cycle Time: só item concluído.
 *
 * <p>O defeito que isto fecha: um item sem histórico de estado aproveitável não recebe {@code
 * num}/{@code den}, o motor caía nos defaults (0 sobre 1) e a razão afundava — a eficiência ficava
 * baixa por falta de dado, não por espera.
 */
class CompletedOnlyPopulationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricsService metrics =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);

  @BeforeEach
  void structure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2025, 1, 1)));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
  }

  private double value(String key) {
    return metrics.cards("p:ana", Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK))).stream()
        .filter(c -> c.definition().key().equals(key))
        .findFirst()
        .orElseThrow()
        .current()
        .value();
  }

  /**
   * 8 h ativas sobre 10 h de ativo+espera = 0,8. A razão sai do motor como fração; a conversão para
   * percentual é da borda, e é por isso que o número aqui é 0,8 e não 80.
   */
  @Test
  void anItemWithoutHistoryDoesNotDragTheRatioDown() {
    events.add(completed("wi:1", 8.0, 2.0));
    assertThat(value("flow_efficiency")).isCloseTo(0.8, Offset.offset(1e-9));

    events.add(noHistory("wi:2"));
    events.add(noHistory("wi:3"));
    events.add(noHistory("wi:4"));

    assertThat(value("flow_efficiency")).isCloseTo(0.8, Offset.offset(1e-9));
  }

  /** Medida parcial de item inacabado ainda pode andar nos dois sentidos; não entra. */
  @Test
  void anUnfinishedItemDoesNotContributePartialPhases() {
    events.add(completed("wi:1", 6.0, 2.0));
    double activeDone = value("active_time");
    double waitDone = value("waiting_time");

    events.add(unfinished("wi:2", 40.0, 30.0));

    assertThat(value("active_time")).isEqualTo(activeDone).isEqualTo(6.0);
    assertThat(value("waiting_time")).isEqualTo(waitDone).isEqualTo(2.0);
    assertThat(value("flow_efficiency")).isCloseTo(0.75, Offset.offset(1e-9));
  }

  /** O WIP é a métrica que fala do inacabado — e ela continua vendo o item. */
  @Test
  void theUnfinishedItemIsStillVisibleThroughWip() {
    events.add(unfinished("wi:2", 40.0, 30.0));

    assertThat(value("wip")).isEqualTo(1.0);
    assertThat(value("active_time")).isZero();
  }

  // ---- fixture ----

  private static RawEvent completed(String id, double activeH, double waitH) {
    Map<String, String> d = phases(activeH, waitH);
    d.put("completed", "1");
    d.put("cycle_h", Double.toString(activeH + waitH));
    return wi(id, d);
  }

  private static RawEvent unfinished(String id, double activeH, double waitH) {
    Map<String, String> d = phases(activeH, waitH);
    d.put("in_progress", "1");
    long start = Instant.parse("2026-06-20T09:00:00Z").toEpochMilli();
    d.put("spans", start + ":" + (start + (long) (activeH * 3_600_000L)));
    return wi(id, d);
  }

  /** Sem transição utilizável: nem num/den, nem fases, nem spans. */
  private static RawEvent noHistory(String id) {
    Map<String, String> d = new HashMap<>();
    d.put("type", "feature");
    d.put("ado_type", "Task");
    return wi(id, d);
  }

  private static Map<String, String> phases(double activeH, double waitH) {
    Map<String, String> d = new HashMap<>();
    d.put("type", "feature");
    d.put("ado_type", "Task");
    d.put("active_h", Double.toString(activeH));
    d.put("wait_h", Double.toString(waitH));
    d.put("review_h", "0.0");
    d.put("num", Double.toString(activeH));
    d.put("den", Double.toString(activeH + waitH));
    return d;
  }

  private static RawEvent wi(String id, Map<String, String> detail) {
    return new RawEvent(
        id,
        EventType.WORKITEM,
        Instant.parse("2026-06-15T10:00:00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        Map.copyOf(detail));
  }
}
