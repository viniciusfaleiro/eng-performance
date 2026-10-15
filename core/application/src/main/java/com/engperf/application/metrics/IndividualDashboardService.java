package com.engperf.application.metrics;

import com.engperf.application.port.inbound.IndividualDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes the individual (person) contribution panel from that person's raw events: a 12-month
 * commit calendar, PR assertiveness (first-pass approvals), the reused delivery + volume series,
 * code-review contribution (given vs received), work-type distribution, and recent activity.
 */
public final class IndividualDashboardService implements IndividualDashboardUseCase {

  private static final int ACTIVITY_LIMIT = 10;
  private static final List<String> DELIVERY_KEYS =
      List.of("throughput", "cycle_time", "ai_share", "commit_count", "pr_count");

  private final StructureRepositoryPort structure;
  private final EventStorePort events;
  private final MetricsQueryUseCase metrics;
  private final UserAccountRepositoryPort accounts;
  private final Clock clock;

  public IndividualDashboardService(
      StructureRepositoryPort structure,
      EventStorePort events,
      MetricsQueryUseCase metrics,
      UserAccountRepositoryPort accounts,
      Clock clock) {
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public IndividualDashboard dashboard(
      String personNodeId, Period period, WorkTypeFilter workTypes) {
    String label = structure.findPerson(personNodeId).map(p -> p.name()).orElse(personNodeId);
    Set<String> identities = identitiesOf(personNodeId);

    LocalDate today = LocalDate.now(clock);
    // As janelas longas terminam no período exibido, não em hoje: encolher o calendário para o mês
    // escolhido destruiria o que ele mostra (regularidade ao longo do tempo), mas deixá-lo
    // terminando
    // hoje enquanto o resto da tela mostra julho seria dizer duas coisas ao mesmo tempo.
    LocalDate reference = period.inProgress(today) ? today : period.end().minusDays(1);
    Instant calFrom = startOf(reference.minusDays(ContributionCalendar.DAYS - 1L));
    Instant calTo = startOf(reference.plusDays(1));
    LocalDate periodStart = period.start();
    Instant periodFrom = startOf(periodStart);
    Instant periodTo = startOf(period.end());
    Instant fetchTo = periodTo.isAfter(calTo) ? periodTo : calTo;

    // One query per type over the widest window (calendar), then slice in memory.
    List<RawEvent> commits = mine(EventType.COMMIT, calFrom, fetchTo, identities);
    List<RawEvent> prs = mine(EventType.PR, calFrom, fetchTo, identities);
    List<RawEvent> workItems = mine(EventType.WORKITEM, calFrom, fetchTo, identities);
    List<RawEvent> reviewsGiven = mine(EventType.REVIEW, calFrom, fetchTo, identities);
    List<RawEvent> reviewsReceived = authoredReviews(calFrom, fetchTo, identities);

    // O filtro vale para o que o gestor abre sobre o período — entrega, assertividade, atividade.
    // O calendário de 12 meses, a distribuição por tipo e o checklist de convenções ficam fora: o
    // calendário mostra regularidade e a distribuição responde "como o período se dividiu", que
    // filtrada seria sempre uma fatia só a 100%.
    List<RawEvent> commitsP = ofTypes(within(commits, periodFrom, periodTo), workTypes);
    List<RawEvent> prsP = ofTypes(within(prs, periodFrom, periodTo), workTypes);
    // Um índice só, do corpus inteiro: a pergunta "este item tem filhas?" não pode ter resposta
    // diferente por janela, senão a mesma User Story é contêiner para quem olha o trimestre e
    // trabalho folha para quem olha a semana.
    // O relógio da leitura, e não o da ingestão: um item ainda aberto fica em progresso até agora,
    // senão as horas dele param na última sincronização e a mesma tela passa a ter duas noções de
    // "em progresso até quando".
    WorkDistribution distribution =
        WorkDistribution.of(
            within(workItems, periodFrom, periodTo),
            events.parentWorkItemIds(),
            periodFrom,
            periodTo,
            clock.instant());

    Delivery delivery = delivery(personNodeId, period, workTypes);

    return new IndividualDashboard(
        personNodeId,
        label,
        assertiveness(prsP),
        ContributionCalendar.of(commits, reference),
        delivery.series(),
        reviewStats(
            within(reviewsGiven, periodFrom, periodTo),
            within(reviewsReceived, periodFrom, periodTo)),
        distribution,
        activity(commitsP, prsP),
        // Coaching flags use the trailing 12 months so a quiet day isn't read as "no activity".
        ConventionFlags.of(
            within(commits, calFrom, calTo),
            within(prs, calFrom, calTo),
            within(workItems, calFrom, calTo),
            within(reviewsReceived, calFrom, calTo),
            distribution),
        PlatformAccessLookup.of(accounts, personNodeId),
        !workTypes.isEmpty(),
        delivery.untypedExcluded());
  }

  /** The person's events of the selected work; everything when nothing is selected. */
  private static List<RawEvent> ofTypes(List<RawEvent> events, WorkTypeFilter workTypes) {
    return workTypes.isEmpty() ? events : events.stream().filter(workTypes::matches).toList();
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private static List<RawEvent> within(List<RawEvent> all, Instant from, Instant to) {
    return all.stream()
        .filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
        .toList();
  }

  private Set<String> identitiesOf(String personNodeId) {
    return structure.findIdentities().stream()
        .filter(i -> personNodeId.equals(i.personId()))
        .map(i -> i.identity())
        .collect(Collectors.toSet());
  }

  private List<RawEvent> mine(EventType type, Instant from, Instant to, Set<String> identities) {
    return events.findByTypeBetween(type, from, to).stream()
        .filter(e -> e.committerIdentity() != null && identities.contains(e.committerIdentity()))
        .toList();
  }

  private List<RawEvent> authoredReviews(Instant from, Instant to, Set<String> identities) {
    return events.findByTypeBetween(EventType.REVIEW, from, to).stream()
        .filter(e -> identities.contains(e.detail().get("author")))
        .toList();
  }

  /** Assertiveness = share of the person's PRs approved with no changes requested. */
  private static double assertiveness(List<RawEvent> prs) {
    if (prs.isEmpty()) {
      return 0.0;
    }
    long firstPass = prs.stream().filter(e -> "1".equals(e.detail().get("first_pass"))).count();
    return (double) firstPass / prs.size() * 100.0;
  }

  private Delivery delivery(String personNodeId, Period period, WorkTypeFilter workTypes) {
    List<MetricSeries> series =
        DELIVERY_KEYS.stream()
            .map(k -> metrics.series(k, personNodeId, period, workTypes))
            .toList();
    long untyped = series.stream().mapToLong(MetricSeries::untypedExcluded).max().orElse(0L);
    return new Delivery(series, untyped);
  }

  /** The delivery series plus how many of the person's events the selection left out. */
  private record Delivery(List<MetricSeries> series, long untypedExcluded) {}

  private static ReviewStats reviewStats(List<RawEvent> given, List<RawEvent> received) {
    int comments = given.stream().mapToInt(e -> intDetail(e, "comments")).sum();
    int approvals =
        (int) given.stream().filter(e -> "approved".equals(e.detail().get("decision"))).count();
    int rejections =
        (int)
            given.stream()
                .filter(e -> "changes_requested".equals(e.detail().get("decision")))
                .count();
    return new ReviewStats(
        comments,
        approvals,
        rejections,
        given.size(),
        received.size(),
        entries(given),
        entries(received));
  }

  /** Mais recentes primeiro: quem abre a lista está conferindo o que acabou de acontecer. */
  private static List<ReviewEntry> entries(List<RawEvent> reviews) {
    return reviews.stream()
        .sorted(Comparator.comparing(RawEvent::occurredAt).reversed())
        .limit(WorkDistribution.ITEM_LIMIT)
        .map(
            e ->
                new ReviewEntry(
                    e.id(),
                    e.detail().getOrDefault("summary", ""),
                    e.detail().getOrDefault("url", ""),
                    e.detail().getOrDefault("decision", ""),
                    e.occurredOn().toString(),
                    intDetail(e, "comments")))
        .toList();
  }

  private static List<ActivityItem> activity(List<RawEvent> commits, List<RawEvent> prs) {
    List<RawEvent> recent = new ArrayList<>();
    recent.addAll(commits);
    recent.addAll(prs);
    recent.sort(Comparator.comparing(RawEvent::occurredAt).reversed());
    return recent.stream()
        .limit(ACTIVITY_LIMIT)
        .map(
            e ->
                new ActivityItem(
                    e.type() == EventType.COMMIT ? "commit" : "pr",
                    e.detail().getOrDefault("summary", ""),
                    e.detail().getOrDefault("repo", ""),
                    e.occurredOn().toString(),
                    e.detail().getOrDefault("url", "")))
        .toList();
  }

  private static int intDetail(RawEvent e, String key) {
    return (int) doubleDetail(e, key);
  }

  private static double doubleDetail(RawEvent e, String key) {
    String v = e.detail().get(key);
    if (v == null) {
      return 0.0;
    }
    try {
      return Double.parseDouble(v);
    } catch (NumberFormatException ex) {
      return 0.0;
    }
  }
}
