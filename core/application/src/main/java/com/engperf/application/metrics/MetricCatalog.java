package com.engperf.application.metrics;

import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.TierBands;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The metric catalog. Non-DORA metrics are representative S3 samples; the four DORA metrics carry
 * benchmark {@link TierBands}. Lead Time reads each deploy's lead hours ({@code value}); MTTR reads
 * only recovery deploys via the {@code recovery_hours} measure; CFR reads {@code num/den} that the
 * source derives from the deploy outcome. The Fluxo/IA slices (S5/S6) add their own metrics.
 */
public final class MetricCatalog {

  /** The DORA metrics in dashboard order. */
  public static final List<String> DORA = List.of("deploy_freq", "lead_time", "cfr", "mttr");

  /** The Fluxo metrics in dashboard-card order: board delivery metrics, then code drill-downs. */
  public static final List<String> FLUXO =
      List.of(
          "cycle_time",
          "throughput",
          "flow_lead_time",
          "wip",
          "flow_efficiency",
          "pr_review_time",
          "pr_size");

  /** The cycle-time segments, in flow order — from the work item's own board states. */
  public static final List<String> PHASES = List.of("waiting_time", "active_time", "review_time");

  /** The IA metrics in dashboard-card order (impact is composed, not a raw engine metric). */
  public static final List<String> IA = List.of("ai_share", "ai_adoption");

  /**
   * AI Impact is not a raw engine metric: it compares cycle time across the AI and non-AI cohorts.
   * This definition carries only its label/unit/direction for the dashboard; the value is composed
   * by {@link AiDashboardService} from the two cohort series.
   */
  public static final MetricDefinition AI_IMPACT =
      new MetricDefinition(
          "ai_impact",
          "Cycle time mais rápido c/ IA",
          "ia",
          EventType.PR,
          AttributionScope.PERSON,
          Aggregation.RATIO,
          "%",
          Direction.HIGHER_BETTER);

  private static final List<MetricDefinition> DEFINITIONS =
      List.of(
          // ---- Fluxo / IA samples (no tiers) ----
          new MetricDefinition(
              // Throughput = count of work items completed (terminal) in the period, via the
              // `completed` population; a work item is the unit of delivered value, not a PR.
              "throughput",
              "Throughput",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.SUM,
              "itens",
              Direction.HIGHER_BETTER),
          new MetricDefinition(
              "pr_review_time",
              "PR Review Time",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "h",
              Direction.LOWER_BETTER),
          new MetricDefinition(
              "ai_share",
              "% de commits com IA",
              "ia",
              EventType.COMMIT,
              AttributionScope.PERSON,
              Aggregation.RATIO,
              "%",
              Direction.HIGHER_BETTER),
          new MetricDefinition(
              "ai_adoption",
              "Adoção de IA (devs)",
              "ia",
              EventType.COMMIT,
              AttributionScope.PERSON,
              Aggregation.DISTINCT_RATIO,
              "%",
              Direction.HIGHER_BETTER),
          new MetricDefinition(
              // WIP = number of work items in progress in the period (count of WORKITEM events in
              // the bucket). A count is concurrency-safe: many simultaneous items can't inflate it
              // the way summing each item's hours did. Lower-is-better; unit "itens", not hours.
              "wip",
              "Work in Progress",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.SUM,
              "itens",
              Direction.LOWER_BETTER),
          // ---- Fluxo (S5): cycle time + phases, PR size, flow efficiency ----
          new MetricDefinition(
              // Cycle Time = median of the work item's first-active → terminal duration.
              "cycle_time",
              "Cycle Time",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "cycle_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              // Flow Lead Time = median of creation → completion; distinct from DORA lead_time.
              "flow_lead_time",
              "Lead Time (fluxo)",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "lead_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "active_time",
              "Ativo",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "active_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "waiting_time",
              "Espera",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "wait_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "review_time",
              "Review",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "review_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "pr_size",
              "PR Size (médio)",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "lines",
              "linhas",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              // Flow Efficiency = working time / (working + wait) over the work item's board life.
              "flow_efficiency",
              "Flow Efficiency",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.RATIO,
              MetricDefinition.VALUE,
              "%",
              Direction.HIGHER_BETTER,
              null),
          // Code drill-downs kept on the PR: the AI dashboard compares AI vs non-AI over these
          // (the AI flag lives on commits/PRs, not work items). Not shown as Fluxo cards.
          new MetricDefinition(
              "code_cycle_time",
              "Cycle Time (código)",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "cycle_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "code_throughput",
              "PRs concluídos",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.SUM,
              "PRs",
              Direction.HIGHER_BETTER),
          // ---- DORA (with benchmark tiers) ----
          new MetricDefinition(
              "deploy_freq",
              "Deployment Frequency",
              "dora",
              EventType.DEPLOY,
              AttributionScope.REPO,
              Aggregation.SUM,
              MetricDefinition.VALUE,
              "deploys",
              Direction.HIGHER_BETTER,
              new TierBands(1.0, 1.0 / 7.0, 1.0 / 30.0)),
          new MetricDefinition(
              "lead_time",
              "Lead Time for Changes",
              "dora",
              EventType.DEPLOY,
              AttributionScope.REPO,
              Aggregation.MEDIAN,
              MetricDefinition.VALUE,
              "h",
              Direction.LOWER_BETTER,
              new TierBands(24, 168, 720)),
          new MetricDefinition(
              "cfr",
              "Change Failure Rate",
              "dora",
              EventType.DEPLOY,
              AttributionScope.REPO,
              Aggregation.RATIO,
              MetricDefinition.VALUE,
              "%",
              Direction.LOWER_BETTER,
              new TierBands(0.15, 0.30, 0.45)),
          new MetricDefinition(
              "mttr",
              "Mean Time to Restore",
              "dora",
              EventType.DEPLOY,
              AttributionScope.REPO,
              Aggregation.MEDIAN,
              "recovery_hours",
              "h",
              Direction.LOWER_BETTER,
              new TierBands(1, 24, 168)));

  public List<MetricDefinition> all() {
    return DEFINITIONS;
  }

  public Optional<MetricDefinition> find(String key) {
    return DEFINITIONS.stream().filter(d -> d.key().equals(key)).findFirst();
  }

  public List<MetricDefinition> dora() {
    return byKeys(DORA);
  }

  public List<MetricDefinition> fluxo() {
    return byKeys(FLUXO);
  }

  public List<MetricDefinition> phases() {
    return byKeys(PHASES);
  }

  /** The IA metrics that come straight from the engine ({@code ai_share}, {@code ai_adoption}). */
  public List<MetricDefinition> ia() {
    return byKeys(IA);
  }

  private List<MetricDefinition> byKeys(List<String> keys) {
    return keys.stream().map(this::find).flatMap(Optional::stream).toList();
  }

  /**
   * The event population a metric aggregates over — a filter beyond its event type. Throughput and
   * Cycle Time count only **completed** work items; WIP counts only **in-progress** ones; every
   * other metric aggregates its whole event type. The engine already supports a population
   * predicate (used for the AI cohort); this wires it per metric.
   */
  public Predicate<RawEvent> population(String key) {
    return POPULATIONS.getOrDefault(key, e -> true);
  }

  private static final Map<String, Predicate<RawEvent>> POPULATIONS =
      Map.of(
          "throughput", completed(),
          "cycle_time", completed(),
          "flow_lead_time", completed(),
          "wip", e -> "1".equals(e.detail().get("in_progress")));

  private static Predicate<RawEvent> completed() {
    return e -> "1".equals(e.detail().get("completed"));
  }
}
