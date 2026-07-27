package com.engperf.application.metrics;

import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.TierBands;
import java.util.List;
import java.util.Optional;

/**
 * The metric catalog. Non-DORA metrics are representative S3 samples; the four DORA metrics carry
 * benchmark {@link TierBands}. Lead Time reads each deploy's lead hours ({@code value}); MTTR reads
 * only recovery deploys via the {@code recovery_hours} measure; CFR reads {@code num/den} that the
 * source derives from the deploy outcome. The Fluxo/IA slices (S5/S6) add their own metrics.
 */
public final class MetricCatalog {

  /** The DORA metrics in dashboard order. */
  public static final List<String> DORA = List.of("deploy_freq", "lead_time", "cfr", "mttr");

  /** The Fluxo metrics in dashboard-card order. */
  public static final List<String> FLUXO =
      List.of("cycle_time", "throughput", "wip", "pr_review_time", "pr_size", "flow_efficiency");

  /** The four cycle-time phases, in flow order (review reuses {@code pr_review_time}). */
  public static final List<String> PHASES =
      List.of("coding_time", "pickup_time", "pr_review_time", "deploy_time");

  private static final List<MetricDefinition> DEFINITIONS =
      List.of(
          // ---- Fluxo / IA samples (no tiers) ----
          new MetricDefinition(
              "throughput",
              "Throughput",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.SUM,
              "PRs",
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
              "wip",
              "Work in Progress",
              "fluxo",
              EventType.WORKITEM,
              AttributionScope.PERSON,
              Aggregation.SNAPSHOT,
              "itens",
              Direction.LOWER_BETTER),
          // ---- Fluxo (S5): cycle time + phases, PR size, flow efficiency ----
          new MetricDefinition(
              "cycle_time",
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
              "coding_time",
              "Coding",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "coding_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "pickup_time",
              "PR Pickup",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "pickup_h",
              "h",
              Direction.LOWER_BETTER,
              null),
          new MetricDefinition(
              "deploy_time",
              "Deploy",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.MEDIAN,
              "deploy_h",
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
              "flow_efficiency",
              "Flow Efficiency",
              "fluxo",
              EventType.PR,
              AttributionScope.PERSON,
              Aggregation.RATIO,
              MetricDefinition.VALUE,
              "%",
              Direction.HIGHER_BETTER,
              null),
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

  private List<MetricDefinition> byKeys(List<String> keys) {
    return keys.stream().map(this::find).flatMap(Optional::stream).toList();
  }
}
