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
    // The contribution calendar is a fixed rolling 12-month map (GitHub-style), intentionally
    // independent of the selected frequency — it always shows the trailing year.
    Instant calFrom = startOf(reference.minusDays(CALENDAR_DAYS - 1L));
    Instant calTo = startOf(reference.plusDays(1));
    // Everything else tracks the SELECTED period = the current bucket of `frequency`, matching the
    // delivery tiles' current value and the panel's "vs. período anterior" framing.
    LocalDate periodStart = frequency.bucketStart(reference);
    Instant periodFrom = startOf(periodStart);
    Instant periodTo = startOf(frequency.nextBucketStart(periodStart));
    Instant fetchTo = periodTo.isAfter(calTo) ? periodTo : calTo;

    // One query per type over the widest window (calendar), then slice in memory.
    List<RawEvent> commits = mine(EventType.COMMIT, calFrom, fetchTo, identities);
    List<RawEvent> prs = mine(EventType.PR, calFrom, fetchTo, identities);
    List<RawEvent> workItems = mine(EventType.WORKITEM, calFrom, fetchTo, identities);
    List<RawEvent> reviewsGiven = mine(EventType.REVIEW, calFrom, fetchTo, identities);
    List<RawEvent> reviewsReceived = authoredReviews(calFrom, fetchTo, identities);

    List<RawEvent> commitsP = within(commits, periodFrom, periodTo);
    List<RawEvent> prsP = within(prs, periodFrom, periodTo);

    return new IndividualDashboard(
        personNodeId,
        label,
        assertiveness(prsP),
        calendar(commits, reference),
        delivery(personNodeId, frequency),
        reviewStats(
            within(reviewsGiven, periodFrom, periodTo),
            within(reviewsReceived, periodFrom, periodTo)),
        workTypes(within(workItems, periodFrom, periodTo), periodFrom, periodTo),
        activity(commitsP, prsP),
        // Coaching flags look at the trailing 12 months, not the selected bucket, so a quiet day
        // does not read as "no activity / unmapped identity".
        conventions(
            within(commits, calFrom, calTo),
            within(prs, calFrom, calTo),
            within(workItems, calFrom, calTo),
            within(reviewsReceived, calFrom, calTo)));
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private static List<RawEvent> within(List<RawEvent> all, Instant from, Instant to) {
    return all.stream()
        .filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
        .toList();
  }

  /**
   * Coaching flags: which agreed conventions (docs/convencoes-adocao-times.xlsx) this person's own
   * activity suggests may be broken, so a manager knows what to check with the dev. Heuristics over
   * the person's events only — never compared with peers. Only raised flags are returned.
   */
  private static List<ConventionFlag> conventions(
      List<RawEvent> commits,
      List<RawEvent> prs,
      List<RawEvent> workItems,
      List<RawEvent> reviewsReceived) {
    // No activity at all → the identity is likely unmapped or the commit email diverges (Grupo A).
    if (commits.isEmpty() && prs.isEmpty() && workItems.isEmpty() && reviewsReceived.isEmpty()) {
      return List.of(
          new ConventionFlag(
              "1",
              "warn",
              "Convenções 1–2 · Identidade e estrutura",
              "Sem atividade atribuída no período",
              "Nenhum commit, PR ou work item chegou a este contribuidor. A identidade de commit"
                  + " pode não estar vinculada à Pessoa (Admin → Identidades) ou o e-mail de commit"
                  + " diverge do cadastrado. Confirme o git user.email do dev.",
              List.of("todas as métricas de pessoa", "cobertura")));
    }
    List<ConventionFlag> flags = new ArrayList<>();
    addIfPresent(flags, aiFlag(commits));
    addIfPresent(flags, prFlag(commits, prs));
    addIfPresent(flags, boardFlag(commits, workItems));
    addIfPresent(flags, reviewFlag(prs, reviewsReceived));
    return flags;
  }

  private static void addIfPresent(List<ConventionFlag> flags, ConventionFlag flag) {
    if (flag != null) {
      flags.add(flag);
    }
  }

  /** Grupo D · 16 — commits but none marked as AI-assisted, so the AI metrics read zero. */
  private static ConventionFlag aiFlag(List<RawEvent> commits) {
    if (commits.isEmpty() || commits.stream().anyMatch(RawEvent::ai)) {
      return null;
    }
    return new ConventionFlag(
        "16",
        "warn",
        "Convenção 16 · Assistência de IA",
        "Nenhum commit marca uso de IA",
        "Nenhum dos "
            + commits.size()
            + " commits do período carrega o trailer de IA. Verifique com o dev se ele usa um"
            + " assistente e aplica o trailer padrão (ex.: Co-authored-by: Copilot); sem ele o"
            + " % de commits com IA fica zerado.",
        List.of("ai_share", "ai_adoption"));
  }

  /** Grupo C · 10 — commits but no PR, i.e. code may be reaching main without a pull request. */
  private static ConventionFlag prFlag(List<RawEvent> commits, List<RawEvent> prs) {
    if (commits.isEmpty() || !prs.isEmpty()) {
      return null;
    }
    return new ConventionFlag(
        "10",
        "warn",
        "Convenção 10 · Fluxo de código",
        "Commits sem pull request",
        commits.size()
            + " commits e nenhum PR no período. O código pode estar entrando na main sem pull"
            + " request, ou os PRs deste dev não estão sendo atribuídos à identidade dele.",
        List.of("throughput", "cycle_time", "assertividade"));
  }

  /** Grupo E · 20/21 — commits without work items, or work items with no usable state history. */
  private static ConventionFlag boardFlag(List<RawEvent> commits, List<RawEvent> workItems) {
    if (!commits.isEmpty() && workItems.isEmpty()) {
      return new ConventionFlag(
          "20",
          "warn",
          "Convenção 20 · Boards",
          "Commits sem work item",
          "Atividade de código sem nenhum work item associado. Está commitando sem task"
              + " aberta/tipada? Sem work item não há distribuição por tipo nem WIP para este dev.",
          List.of("distribuição por tipo", "wip"));
    }
    if (!workItems.isEmpty()
        && workItems.stream().noneMatch(w -> w.detail().containsKey("hours"))) {
      return new ConventionFlag(
          "21",
          "info",
          "Convenção 21 · Boards",
          "Board sem transição de estado utilizável",
          "Os "
              + workItems.size()
              + " work items não têm transição de estado aproveitável — o board pode não separar"
              + " 'fazendo' de 'esperando', ou os cards são movidos em lote. WIP e tempo por tipo"
              + " ficam sem dado.",
          List.of("wip", "flow_efficiency", "cycle time de WI"));
    }
    return null;
  }

  /** Grupo C · 13 — the person authored PRs but none carries a registered review. */
  private static ConventionFlag reviewFlag(List<RawEvent> prs, List<RawEvent> reviewsReceived) {
    if (prs.isEmpty() || !reviewsReceived.isEmpty()) {
      return null;
    }
    return new ConventionFlag(
        "13",
        "warn",
        "Convenção 13 · Fluxo de código",
        "PRs sem review registrada",
        "Os PRs deste dev não têm nenhuma review registrada no período. A review pode estar"
            + " acontecendo fora do PR (chat/call), o que zera o tempo de review e a assertividade.",
        List.of("pr_review_time", "% PRs sem review", "assertividade"));
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

  private static List<WorkTypeSlice> workTypes(List<RawEvent> workItems, Instant from, Instant to) {
    Map<String, Double> hoursByType = new LinkedHashMap<>();
    for (Map.Entry<String, String> t : WORK_TYPES) {
      hoursByType.put(t.getKey(), 0.0);
    }
    for (RawEvent w : workItems) {
      Double hours = itemHours(w, from, to);
      if (hours == null) {
        continue; // no usable state history → "no data", excluded from the distribution (not zero)
      }
      String type = w.detail().getOrDefault("type", "docs");
      hoursByType.merge(hoursByType.containsKey(type) ? type : "docs", hours, Double::sum);
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

  /**
   * The item's in-progress hours <b>within</b> {@code [from, to)} — clipped from its stored {@code
   * spans} so a long-open item counts only its overlap with the period, not its whole life. Falls
   * back to the unclipped total for legacy events with no spans; {@code null} when the item has no
   * usable state history (excluded from the distribution as "no data", never a silent zero).
   */
  private static Double itemHours(RawEvent w, Instant from, Instant to) {
    String spans = w.detail().get("spans");
    if (spans != null) {
      return clippedHours(spans, from, to);
    }
    return w.detail().containsKey("hours") ? doubleDetail(w, "hours") : null;
  }

  /**
   * Sums the overlap of each {@code from:to} epoch-milli span with {@code [windowFrom, windowTo)}.
   */
  private static double clippedHours(String spans, Instant windowFrom, Instant windowTo) {
    long lo0 = windowFrom.toEpochMilli();
    long hi0 = windowTo.toEpochMilli();
    double millis = 0;
    for (String part : spans.split(",")) {
      int c = part.indexOf(':');
      if (c < 0) {
        continue; // empty (transitioned but no in-progress time) → contributes zero
      }
      long a = Long.parseLong(part.substring(0, c));
      long b = Long.parseLong(part.substring(c + 1));
      long lo = Math.max(a, lo0);
      long hi = Math.min(b, hi0);
      if (hi > lo) {
        millis += hi - lo;
      }
    }
    return millis / 3_600_000.0;
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
