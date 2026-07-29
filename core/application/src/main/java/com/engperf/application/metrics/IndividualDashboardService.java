package com.engperf.application.metrics;

import com.engperf.application.port.inbound.IndividualDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.RawEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes the individual (person) contribution panel from that person's raw events: a 12-month
 * commit calendar, PR assertiveness (first-pass approvals), the reused delivery series, code-review
 * contribution (given vs received), the work-type distribution, and recent activity for the drawer.
 * Mirrors {@link MetricsService} in taking the structure/event ports and a fixed {@link Clock}.
 */
public final class IndividualDashboardService implements IndividualDashboardUseCase {

  private static final int CALENDAR_DAYS = 371; // 53 weeks × 7, aligned to the reference date
  private static final int ACTIVITY_LIMIT = 10;

  /** Work-item types in the prototype's legend order, with their display labels. */
  static final List<Map.Entry<String, String>> WORK_TYPES =
      List.of(
          Map.entry("feature", "Feature"),
          Map.entry("bug", "Bug"),
          Map.entry("tech_debt", "Tech Debt"),
          Map.entry("maintenance", "Manutenção"),
          Map.entry("docs", "Docs/Outros"));

  private final StructureRepositoryPort structure;
  private final EventStorePort events;
  private final MetricsQueryUseCase metrics;
  private final Clock clock;

  public IndividualDashboardService(
      StructureRepositoryPort structure,
      EventStorePort events,
      MetricsQueryUseCase metrics,
      Clock clock) {
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public IndividualDashboard dashboard(String personNodeId, Frequency frequency) {
    String label = structure.findPerson(personNodeId).map(p -> p.name()).orElse(personNodeId);
    Set<String> identities = identitiesOf(personNodeId);

    LocalDate reference = LocalDate.now(clock);
    Instant from = reference.minusDays(CALENDAR_DAYS - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = reference.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    List<RawEvent> commits = mine(EventType.COMMIT, from, to, identities);
    List<RawEvent> prs = mine(EventType.PR, from, to, identities);
    List<RawEvent> workItems = mine(EventType.WORKITEM, from, to, identities);
    List<RawEvent> reviewsGiven = mine(EventType.REVIEW, from, to, identities);
    List<RawEvent> reviewsReceived = authoredReviews(from, to, identities);

    return new IndividualDashboard(
        personNodeId,
        label,
        assertiveness(prs),
        calendar(commits, reference),
        delivery(personNodeId, frequency),
        reviewStats(reviewsGiven, reviewsReceived),
        workTypes(workItems),
        activity(commits, prs));
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

  private static List<CalendarDay> calendar(List<RawEvent> commits, LocalDate reference) {
    Map<LocalDate, Integer> byDay = new LinkedHashMap<>();
    LocalDate start = reference.minusDays(CALENDAR_DAYS - 1L);
    for (LocalDate d = start; !d.isAfter(reference); d = d.plusDays(1)) {
      byDay.put(d, 0);
    }
    for (RawEvent c : commits) {
      byDay.computeIfPresent(c.occurredOn(), (d, n) -> n + 1);
    }
    return byDay.entrySet().stream()
        .map(e -> new CalendarDay(e.getKey().toString(), e.getValue()))
        .toList();
  }

  private List<MetricSeries> delivery(String personNodeId, Frequency frequency) {
    return List.of("throughput", "cycle_time", "ai_share").stream()
        .map(key -> metrics.series(key, personNodeId, frequency))
        .toList();
  }

  private static ReviewStats reviewStats(List<RawEvent> given, List<RawEvent> received) {
    int comments = given.stream().mapToInt(e -> intDetail(e, "comments")).sum();
    int approvals =
        (int) given.stream().filter(e -> "approved".equals(e.detail().get("decision"))).count();
    int rejections =
        (int)
            given.stream()
                .filter(e -> "changes_requested".equals(e.detail().get("decision")))
                .count();
    return new ReviewStats(comments, approvals, rejections, given.size(), received.size());
  }

  private static List<WorkTypeSlice> workTypes(List<RawEvent> workItems) {
    Map<String, Double> hoursByType = new LinkedHashMap<>();
    for (Map.Entry<String, String> t : WORK_TYPES) {
      hoursByType.put(t.getKey(), 0.0);
    }
    for (RawEvent w : workItems) {
      if (!w.detail().containsKey("hours")) {
        continue; // no usable state history → "no data", excluded from the distribution (not zero)
      }
      String type = w.detail().getOrDefault("type", "docs");
      hoursByType.merge(
          hoursByType.containsKey(type) ? type : "docs", doubleDetail(w, "hours"), Double::sum);
    }
    double total = hoursByType.values().stream().mapToDouble(Double::doubleValue).sum();
    List<WorkTypeSlice> slices = new ArrayList<>();
    for (Map.Entry<String, String> t : WORK_TYPES) {
      double hours = hoursByType.get(t.getKey());
      double share = total == 0.0 ? 0.0 : hours / total * 100.0;
      slices.add(new WorkTypeSlice(t.getKey(), t.getValue(), hours, share));
    }
    return slices;
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
