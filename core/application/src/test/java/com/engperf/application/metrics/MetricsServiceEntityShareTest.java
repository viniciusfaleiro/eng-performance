package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import org.junit.jupiter.api.Test;

/**
 * A adoção de IA é medida em pessoas, não em commits — então detalhá-la tem de listar pessoas, e a
 * lista tem de somar à mesma população que o card divide.
 */
class MetricsServiceEntityShareTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
  private static final Period JUNE = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 6, 15));

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricsService service = new MetricsService(structure, events, catalog, CLOCK);

  private int seq = 0;

  private void baseStructure() {
    structure.verticals.add(new Vertical("v:eng", "Eng", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:eng", null, null));
    structure.teams.add(new Team("t:pay", "Pay", "v:eng", null, null));
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", JAN1));
    structure.people.add(Person.create("p:bruno", "Bruno", null, "t:checkout", JAN1));
    structure.people.add(Person.create("p:carla", "Carla", null, "t:pay", JAN1));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
    structure.identities.add(new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0));
    structure.identities.add(new CommitterIdentity("id-carla", "Carla", "p:carla", 0));
  }

  @Test
  void peopleAreRankedFromTheLowestShareToTheHighest() {
    baseStructure();
    events.add(commit("id-ana", true));
    events.add(commit("id-ana", true));
    events.add(commit("id-bruno", true));
    events.add(commit("id-bruno", false));
    events.add(commit("id-carla", false));

    List<EntityShare> shares = service.entityShares("ai_adoption", "all", JUNE);

    assertThat(shares)
        .extracting(EntityShare::entityId, EntityShare::matching, EntityShare::total)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("p:carla", 0L, 1L), // 0%
            org.assertj.core.groups.Tuple.tuple("p:bruno", 1L, 2L), // 50%
            org.assertj.core.groups.Tuple.tuple("p:ana", 2L, 2L)); // 100%
  }

  /**
   * O detalhamento tem de explicar o número, não apenas acompanhá-lo: a razão entre quem tem algum
   * commit com IA e o total de pessoas listadas é exatamente o percentual do card.
   */
  @Test
  void theBreakdownAddsUpToTheCardValue() {
    baseStructure();
    events.add(commit("id-ana", true));
    events.add(commit("id-bruno", false));
    events.add(commit("id-carla", false));

    List<EntityShare> shares = service.entityShares("ai_adoption", "all", JUNE);
    double fromList =
        (double) shares.stream().filter(s -> s.matching() > 0).count() / shares.size();
    double card =
        service.cards("all", JUNE).stream()
            .filter(c -> c.definition().key().equals("ai_adoption"))
            .mapToDouble(c -> c.current().value())
            .findFirst()
            .orElseThrow();

    assertThat(fromList).isEqualTo(card).isEqualTo(1.0 / 3);
  }

  /** Quem não commitou no período não é "não usuário de IA" — não produziu código, e só. */
  @Test
  void someoneWithNoCommitInThePeriodIsNotListed() {
    baseStructure();
    events.add(commit("id-ana", true));

    assertThat(service.entityShares("ai_adoption", "all", JUNE))
        .extracting(EntityShare::entityId)
        .containsExactly("p:ana")
        .doesNotContain("p:bruno", "p:carla");
  }

  @Test
  void theListingFollowsTheSelectedNode() {
    baseStructure();
    events.add(commit("id-ana", true));
    events.add(commit("id-carla", false));

    assertThat(service.entityShares("ai_adoption", "t:checkout", JUNE))
        .extracting(EntityShare::entityId)
        .containsExactly("p:ana");
    assertThat(service.entityShares("ai_adoption", "t:pay", JUNE))
        .extracting(EntityShare::entityId)
        .containsExactly("p:carla");
    assertThat(service.entityShares("ai_adoption", "v:eng", JUNE)).hasSize(2);
  }

  @Test
  void aMetricCountedInEventsIsRefused() {
    baseStructure();

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> service.entityShares("commit_count", "all", JUNE))
        .withMessageContaining("não é contada por pessoa");
  }

  private RawEvent commit(String identity, boolean ai) {
    return new RawEvent(
        "c" + (seq++),
        EventType.COMMIT,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        ai,
        Map.of());
  }
}
