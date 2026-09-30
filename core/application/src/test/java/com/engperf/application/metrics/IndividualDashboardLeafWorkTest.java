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
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A regra de folha e as listas auditáveis da distribuição.
 *
 * <p>O caso que motivou tudo isto: numa janela de 30 dias (720 h corridas), uma Feature e duas User
 * Stories que só agrupavam Tasks responderam por 1.768 h das 2.382 h reportadas, disputando com as
 * Tasks as mesmas horas. O que estes testes fixam não é o total — é que contêiner não compete.
 */
class IndividualDashboardLeafWorkTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeStructure structure = new FakeStructure();
  private final FakeEvents events = new FakeEvents();
  private final FakeAccounts accounts = new FakeAccounts();
  private final MetricsService metrics =
      new MetricsService(structure, events, new MetricCatalog(), CLOCK);
  private final IndividualDashboardService individual =
      new IndividualDashboardService(structure, events, metrics, accounts, CLOCK);

  @BeforeEach
  void structure() {
    structure.verticals.add(new Vertical("v:pag", "Pagamentos", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:pag", null, null));
    structure.people.add(
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1)));
    structure.identities.add(new CommitterIdentity("id-ana", "Ana", "p:ana", 0));
  }

  private WorkDistribution distribution() {
    return individual
        .dashboard("p:ana", Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK)))
        .distribution();
  }

  /**
   * O caso do pedido: a mãe fica aberta as mesmas 10 h que as duas filhas. Com a mãe competindo,
   * cada hora se divide por três e as filhas perdem um terço do que fizeram; sem ela, as 10 h se
   * dividem entre as duas filhas, que é o trabalho que existiu.
   */
  @Test
  void aContainerDoesNotCompeteForHoursWithTheWorkItGroups() {
    events.add(item("wi:1", "User Story", "feature", null, "2026-06-10T00:00", "2026-06-10T10:00"));
    events.add(item("wi:2", "Task", "feature", "wi:1", "2026-06-10T00:00", "2026-06-10T10:00"));
    events.add(item("wi:3", "Task", "feature", "wi:1", "2026-06-10T00:00", "2026-06-10T10:00"));

    WorkDistribution d = distribution();
    assertThat(total(d)).isCloseTo(10.0, Offset.offset(1e-9));
    assertThat(d.containersExcluded()).isEqualTo(1);
    assertThat(ids(d, "feature")).containsExactlyInAnyOrder("wi:2", "wi:3");
  }

  /** Um time que trabalha direto numa US ou Feature é medido, não reportado como parado. */
  @Test
  void anItemWorkedOnDirectlyStillCounts() {
    events.add(item("wi:1", "User Story", "feature", null, "2026-06-10T00:00", "2026-06-10T04:00"));
    events.add(item("wi:2", "Feature", "feature", null, "2026-06-11T00:00", "2026-06-11T02:00"));

    assertThat(total(distribution())).isCloseTo(6.0, Offset.offset(1e-9));
    assertThat(distribution().containersExcluded()).isZero();
  }

  /** Contêiner é quem tem filhas, não quem tem um tipo na lista — um Bug com Tasks também é. */
  @Test
  void aBugWithChildTasksIsAContainerToo() {
    events.add(item("wi:1", "Bug", "bug", null, "2026-06-10T00:00", "2026-06-10T08:00"));
    events.add(item("wi:2", "Task", "bug", "wi:1", "2026-06-10T00:00", "2026-06-10T08:00"));

    WorkDistribution d = distribution();
    assertThat(ids(d, "bug")).containsExactly("wi:2");
    assertThat(total(d)).isCloseTo(8.0, Offset.offset(1e-9));
  }

  /** Um Epic é artefato de planejamento mesmo vazio; contá-lo mediria a existência de um plano. */
  @Test
  void anEpicNeverContributesEvenWithoutChildren() {
    events.add(item("wi:1", "Epic", "feature", null, "2026-06-10T00:00", "2026-06-10T08:00"));
    events.add(item("wi:2", "Task", "bug", null, "2026-06-11T00:00", "2026-06-11T02:00"));

    WorkDistribution d = distribution();
    assertThat(total(d)).isCloseTo(2.0, Offset.offset(1e-9));
    assertThat(d.containersExcluded()).isEqualTo(1);
    assertThat(ids(d, "feature")).isEmpty();
  }

  /**
   * A classificação é do corpus, não da janela. A filha rodou em janeiro e nem aparece no período —
   * se o índice fosse do período, a mãe voltaria a contar e o mesmo item seria contêiner para quem
   * olha o trimestre e trabalho folha para quem olha junho.
   */
  @Test
  void childrenOutsideThePeriodStillMakeTheParentAContainer() {
    events.add(item("wi:1", "User Story", "feature", null, "2026-06-10T00:00", "2026-06-10T08:00"));
    events.add(item("wi:2", "Task", "feature", "wi:1", "2026-01-05T00:00", "2026-01-05T08:00"));

    WorkDistribution d = distribution();
    assertThat(total(d)).isZero();
    assertThat(d.containersExcluded()).isEqualTo(1);
  }

  /**
   * A degradação antes do reprocessamento tem de ser o comportamento de hoje, não algo pior: um
   * deploy sem reprocessar não pode produzir número novo e errado.
   */
  @Test
  void withNoParentRecordedAtAllNothingIsExcluded() {
    events.add(item("wi:1", "User Story", "feature", null, "2026-06-10T00:00", "2026-06-10T10:00"));
    events.add(item("wi:2", "Task", "feature", null, "2026-06-10T00:00", "2026-06-10T10:00"));

    WorkDistribution d = distribution();
    assertThat(d.containersExcluded()).isZero();
    assertThat(total(d)).isCloseTo(10.0, Offset.offset(1e-9)); // as 10 h divididas entre os dois
    assertThat(ids(d, "feature")).containsExactlyInAnyOrder("wi:1", "wi:2");
  }

  /** "Não fez trabalho folha" é uma resposta diferente de "não fez nada". */
  @Test
  void aPersonWithOnlyContainersSaysSoInsteadOfShowingZeros() {
    events.add(item("wi:1", "User Story", "feature", null, "2026-06-10T00:00", "2026-06-10T08:00"));
    events.add(item("wi:2", "Task", "feature", "wi:1", "2026-01-05T00:00", "2026-01-05T08:00"));

    WorkDistribution d = distribution();
    assertThat(total(d)).isZero();
    assertThat(d.containersExcluded()).isEqualTo(1);
    assertThat(d.types()).hasSize(5).allSatisfy(t -> assertThat(t.items()).isEmpty());
  }

  /**
   * As duas horas por item, que é o que torna a conta auditável: cada um ficou 10 h aberto e
   * contabilizou 5 h. Sem a corrida, o total não se explica; sem a contabilizada, o item parece
   * errado.
   */
  @Test
  void eachItemCarriesBothItsElapsedAndItsCountedHours() {
    events.add(item("wi:2", "Task", "feature", null, "2026-06-10T00:00", "2026-06-10T10:00"));
    events.add(item("wi:3", "Task", "feature", null, "2026-06-10T00:00", "2026-06-10T10:00"));

    WorkTypeSlice feature = slice(distribution(), "feature");
    assertThat(feature.itemCount()).isEqualTo(2);
    assertThat(feature.items())
        .allSatisfy(
            i -> {
              assertThat(i.elapsedHours()).isCloseTo(10.0, Offset.offset(1e-9));
              assertThat(i.countedHours()).isCloseTo(5.0, Offset.offset(1e-9));
            });
    // A coluna contabilizada é a que fecha com a fatia — é ela que a lista tem de somar.
    assertThat(feature.items().stream().mapToDouble(WorkItemEntry::countedHours).sum())
        .isCloseTo(feature.hours(), Offset.offset(1e-9));
  }

  /** O item de minutos é sinalizado e continua contado: explicar a fatia, não esconder o item. */
  @Test
  void aShortLivedItemIsFlaggedAndStillCounted() {
    events.add(item("wi:1", "Task", "bug", null, "2026-06-10T10:00", "2026-06-10T10:05"));

    var dash = individual.dashboard("p:ana", Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK)));
    assertThat(dash.distribution().shortItems())
        .extracting(WorkItemEntry::id)
        .containsExactly("wi:1");
    assertThat(slice(dash.distribution(), "bug").itemCount()).isEqualTo(1);
    assertThat(dash.conventions())
        .anySatisfy(
            f -> {
              assertThat(f.title()).contains("poucos minutos");
              assertThat(f.severity()).isEqualTo("info"); // pergunta, não acusação
            });
  }

  @Test
  void anItemOfPlausibleDurationIsNotFlagged() {
    events.add(item("wi:1", "Task", "bug", null, "2026-06-10T10:00", "2026-06-10T14:00"));

    var dash = individual.dashboard("p:ana", Period.of(Frequency.MONTHLY, LocalDate.now(CLOCK)));
    assertThat(dash.distribution().shortItems()).isEmpty();
    assertThat(dash.conventions()).noneMatch(f -> f.title().contains("poucos minutos"));
  }

  private static double total(WorkDistribution d) {
    return d.types().stream().mapToDouble(WorkTypeSlice::hours).sum();
  }

  private static WorkTypeSlice slice(WorkDistribution d, String type) {
    return d.types().stream().filter(t -> t.type().equals(type)).findFirst().orElseThrow();
  }

  private static List<String> ids(WorkDistribution d, String type) {
    return slice(d, type).items().stream().map(WorkItemEntry::id).toList();
  }

  private static RawEvent item(
      String id, String adoType, String type, String parentEventId, String from, String to) {
    long a = Instant.parse(from + ":00Z").toEpochMilli();
    long b = Instant.parse(to + ":00Z").toEpochMilli();
    Map<String, String> detail = new HashMap<>();
    detail.put("type", type);
    detail.put("ado_type", adoType);
    detail.put("spans", a + ":" + b);
    detail.put("summary", "item " + id);
    detail.put("url", "https://dev.azure.com/org/p/_workitems/edit/" + id);
    if (parentEventId != null) {
      detail.put("parent_event_id", parentEventId);
    }
    return new RawEvent(
        id,
        EventType.WORKITEM,
        Instant.parse(to + ":00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        Map.copyOf(detail));
  }
}
