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
 * As três fases do cycle time somam o card — e quem fica de fora.
 *
 * <p>O sintoma que motivou isto: 21 h de Cycle Time ao lado de fases somando 1,5 h. Duas causas
 * somadas — item fechado sem passar por trabalho entrava nas fases com zero (porque zero não é
 * ausência), e mediana por fase não soma a mediana do todo.
 */
class CycleTimePhasesTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService metrics = new MetricsService(structure, events, catalog, CLOCK);
  private final FlowDashboardService flow = new FlowDashboardService(metrics, catalog, structure);

  private int seq = 0;

  @BeforeEach
  void baseStructure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1)));
    structure.people.add(
        Person.create("p:bruno", "Bruno", null, "t:checkout", LocalDate.of(2026, 1, 1)));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    structure.identities.add(new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0));
  }

  private static Period period(Frequency f) {
    return Period.of(f, LocalDate.now(CLOCK));
  }

  /** Um item concluído com histórico de estados: o que o card de Cycle Time conta. */
  private RawEvent doneItem(String identity, double active, double review, double wait) {
    double cycle = active + review + wait;
    double working = active + review;
    Map<String, String> detail = new HashMap<>();
    detail.put("completed", "1");
    detail.put("type", "feature");
    detail.put("active_h", Double.toString(active));
    detail.put("review_h", Double.toString(review));
    detail.put("wait_h", Double.toString(wait));
    detail.put("cycle_h", Double.toString(cycle));
    detail.put("lead_h", Double.toString(cycle));
    detail.put("num", Double.toString(working));
    detail.put("den", Double.toString(cycle));
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        Map.copyOf(detail));
  }

  /** E1: 300/100/100 de população com card de 20 h → 12/4/4, que somam o card. */
  @Test
  void thePhasesAddUpToTheCycleTimeCard() {
    // Dois itens que, somados, dão 300 h de espera, 100 de ativo e 100 de review, com ciclos de
    // 20 h e 480 h: a mediana dos dois é 250 h... o que importa aqui é a proporção 60/20/20.
    events.add(doneItem("id-ana", 50, 50, 150));
    events.add(doneItem("id-bruno", 50, 50, 150));

    var dash = flow.dashboard("t:checkout", period(Frequency.MONTHLY));
    double cycle =
        dash.cards().stream()
            .filter(c -> c.definition().key().equals("cycle_time"))
            .mapToDouble(c -> c.value().value())
            .findFirst()
            .orElseThrow();

    assertThat(dash.phases()).extracting(PhaseSlice::sharePct).containsExactly(60.0, 20.0, 20.0);
    assertThat(dash.phases().stream().mapToDouble(PhaseSlice::hours).sum())
        .as("as três somam o card")
        .isCloseTo(cycle, Offset.offset(1e-9));
  }

  /**
   * E2, o sintoma relatado: oito itens fechados direto, sem passar por trabalho, e dois com ciclo.
   * Os oito não gravam cycle_h — saíam do card por medida ausente e entravam nas fases com zero,
   * derrubando as medianas para perto de zero ao lado de um Cycle Time de dezenas de horas.
   */
  @Test
  void itemsClosedWithoutEverBeingWorkedDoNotDragThePhasesDown() {
    for (int i = 0; i < 8; i++) {
      events.add(closedWithoutWork("id-ana"));
    }
    events.add(doneItem("id-ana", 2, 0, 40));
    events.add(doneItem("id-bruno", 3, 1, 30));

    var dash = flow.dashboard("t:checkout", period(Frequency.MONTHLY));
    double cycle =
        dash.cards().stream()
            .filter(c -> c.definition().key().equals("cycle_time"))
            .mapToDouble(c -> c.value().value())
            .findFirst()
            .orElseThrow();
    double throughput =
        dash.cards().stream()
            .filter(c -> c.definition().key().equals("throughput"))
            .mapToDouble(c -> c.value().value())
            .findFirst()
            .orElseThrow();

    assertThat(cycle).as("mediana(42, 34)").isEqualTo(38.0);
    assertThat(throughput).as("os fechados direto continuam entregues").isEqualTo(10.0);
    assertThat(dash.phases())
        .extracting(PhaseSlice::key)
        .containsExactly("waiting_time", "active_time", "review_time");
    assertThat(dash.phases().get(0).hours()).isCloseTo(35.0, Offset.offset(0.05));
    assertThat(dash.phases().get(1).hours()).isCloseTo(2.5, Offset.offset(0.05));
    assertThat(dash.phases().get(2).hours()).isCloseTo(0.5, Offset.offset(0.05));
    assertThat(dash.phases().stream().mapToDouble(PhaseSlice::hours).sum())
        .isCloseTo(cycle, Offset.offset(1e-9));
  }

  /** E6: sem horas de fase na população, as três são zero — não se inventa divisão. */
  @Test
  void withoutPhaseHoursTheBreakdownIsZero() {
    events.add(closedWithoutWork("id-ana"));

    var dash = flow.dashboard("t:checkout", period(Frequency.MONTHLY));

    assertThat(dash.phases()).extracting(PhaseSlice::hours).containsExactly(0.0, 0.0, 0.0);
    assertThat(dash.phases()).extracting(PhaseSlice::sharePct).containsExactly(0.0, 0.0, 0.0);
  }

  /** Item concluído sem passar por trabalho: entregue, mas sem ciclo e sem fase. */
  private RawEvent closedWithoutWork(String identity) {
    Map<String, String> detail = new HashMap<>();
    detail.put("completed", "1");
    detail.put("type", "feature");
    detail.put("active_h", "0.0");
    detail.put("review_h", "0.0");
    detail.put("wait_h", "0.0");
    detail.put("num", "0.0");
    detail.put("den", "0.0");
    return new RawEvent(
        "w" + (seq++),
        EventType.WORKITEM,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        Map.copyOf(detail));
  }
}
