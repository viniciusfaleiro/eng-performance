package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
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
 * O WIP lê o trecho <em>comprometido</em> do item, não os trechos em que alguém estava com a mão
 * nele. É o que faz item parado em fila de deploy aparecer e item impedido sumir — as duas coisas
 * que a métrica existe para mostrar e escondia.
 *
 * <p>Relógio fixo em 15/06/2026 12:00 UTC.
 */
class WipCommittedStretchTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

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

  private double wip(ContainerFilter containers) {
    return metrics
        .cards(
            "p:ana",
            Period.of(Frequency.DAILY, LocalDate.now(CLOCK)),
            WorkTypeFilter.none(),
            containers)
        .stream()
        .filter(c -> c.definition().key().equals("wip"))
        .findFirst()
        .orElseThrow()
        .current()
        .value();
  }

  private double wip() {
    return wip(ContainerFilter.none());
  }

  /** Pronto e não entregue: nenhum trecho de trabalho hoje, mas o item é do time e está aberto. */
  @Test
  void anItemParkedInADeliveryQueueCounts() {
    Map<String, String> d = base();
    d.put("spans", ms("2026-06-01T09:00:00Z") + ":" + ms("2026-06-05T17:00:00Z"));
    d.put("committed_spans", ms("2026-06-01T09:00:00Z") + ":" + ms("2026-06-15T09:00:00Z"));
    d.put("in_progress", "1");
    events.add(wi("wi:1", "2026-06-05T17:00:00Z", d));

    assertThat(wip()).isEqualTo(1.0);
  }

  /** O mesmo item sem o trecho comprometido — como era antes — não aparece hoje. */
  @Test
  void theSameItemWithoutTheCommittedStretchFallsBackToTheWorkingOnes() {
    Map<String, String> d = base();
    d.put("spans", ms("2026-06-01T09:00:00Z") + ":" + ms("2026-06-05T17:00:00Z"));
    d.put("completed", "1");
    events.add(wi("wi:1", "2026-06-05T17:00:00Z", d));

    assertThat(wip()).as("evento antigo cai no fallback de spans").isZero();
  }

  /** Impedido o dia inteiro: o trecho comprometido tem o buraco, e o item não conta. */
  @Test
  void anItemImpededForTheWholePeriodDoesNotCount() {
    Map<String, String> d = base();
    d.put("spans", ms("2026-06-01T09:00:00Z") + ":" + ms("2026-06-20T17:00:00Z"));
    d.put(
        "committed_spans",
        ms("2026-06-01T09:00:00Z")
            + ":"
            + ms("2026-06-14T00:00:00Z")
            + ","
            + ms("2026-06-16T00:00:00Z")
            + ":"
            + ms("2026-06-20T17:00:00Z"));
    d.put("completed", "1");
    events.add(wi("wi:1", "2026-06-20T17:00:00Z", d));

    assertThat(wip()).isZero();
  }

  /** O filtro de contêiner nos três estados, sobre o mesmo par mãe/filha. */
  @Test
  void theContainerSelectionPicksTheSide() {
    events.add(committed("wi:1", "User Story"));
    events.add(child("wi:2", "wi:1"));

    assertThat(wip(ContainerFilter.ALL)).isEqualTo(2.0);
    assertThat(wip(ContainerFilter.LEAVES)).isEqualTo(1.0);
    assertThat(wip(ContainerFilter.CONTAINERS)).isEqualTo(1.0);
  }

  /** Epic é contêiner por tipo, mesmo sem filha ingerida — artefato de planejamento. */
  @Test
  void anEpicIsAContainerEvenWithoutChildren() {
    events.add(committed("wi:9", "Epic"));

    assertThat(wip(ContainerFilter.ALL)).isEqualTo(1.0);
    assertThat(wip(ContainerFilter.LEAVES)).isZero();
    assertThat(wip(ContainerFilter.CONTAINERS)).isEqualTo(1.0);
  }

  // ---- helpers ----

  private static RawEvent committed(String id, String adoType) {
    Map<String, String> d = base();
    d.put("ado_type", adoType);
    d.put("committed_spans", ms("2026-06-10T09:00:00Z") + ":" + ms("2026-06-15T09:00:00Z"));
    d.put("in_progress", "1");
    return wi(id, "2026-06-10T09:00:00Z", d);
  }

  private static RawEvent child(String id, String parent) {
    Map<String, String> d = base();
    d.put("ado_type", "Task");
    d.put("parent_event_id", parent);
    d.put("committed_spans", ms("2026-06-10T09:00:00Z") + ":" + ms("2026-06-15T09:00:00Z"));
    d.put("in_progress", "1");
    return wi(id, "2026-06-10T09:00:00Z", d);
  }

  private static Map<String, String> base() {
    Map<String, String> d = new HashMap<>();
    d.put("type", "feature");
    d.put("ado_type", "Task");
    return d;
  }

  private static long ms(String iso) {
    return Instant.parse(iso).toEpochMilli();
  }

  private static RawEvent wi(String id, String recordedAt, Map<String, String> detail) {
    return new RawEvent(
        id,
        EventType.WORKITEM,
        Instant.parse(recordedAt),
        null,
        "id-ana",
        null,
        null,
        false,
        Map.copyOf(detail));
  }
}
