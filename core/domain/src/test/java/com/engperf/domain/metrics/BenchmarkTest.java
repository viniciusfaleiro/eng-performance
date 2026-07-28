package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BenchmarkTest {

  private static MetricDefinition dora(
      String key, Aggregation agg, Direction dir, TierBands bands) {
    return new MetricDefinition(
        key,
        key,
        "dora",
        EventType.DEPLOY,
        AttributionScope.REPO,
        agg,
        MetricDefinition.VALUE,
        "u",
        dir,
        bands);
  }

  private static final MetricDefinition LEAD_TIME =
      dora("lead_time", Aggregation.MEDIAN, Direction.LOWER_BETTER, new TierBands(24, 168, 720));
  private static final MetricDefinition CFR =
      dora("cfr", Aggregation.RATIO, Direction.LOWER_BETTER, new TierBands(15, 30, 45));
  private static final MetricDefinition MTTR =
      dora("mttr", Aggregation.MEDIAN, Direction.LOWER_BETTER, new TierBands(1, 24, 168));
  private static final MetricDefinition DEPLOY_FREQ =
      dora(
          "deploy_freq",
          Aggregation.SUM,
          Direction.HIGHER_BETTER,
          new TierBands(1.0, 1.0 / 7, 1.0 / 30));

  @Test
  void leadTimeTiersLowerBetter() {
    assertThat(tier(LEAD_TIME, 10, 1)).contains(Tier.ELITE); // < 24h
    assertThat(tier(LEAD_TIME, 100, 1)).contains(Tier.ALTO); // < 168h
    assertThat(tier(LEAD_TIME, 500, 1)).contains(Tier.MEDIO); // < 720h
    assertThat(tier(LEAD_TIME, 900, 1)).contains(Tier.BAIXO); // >= 720h
  }

  @Test
  void changeFailureRateTiers() {
    assertThat(tier(CFR, 12, 1)).contains(Tier.ELITE); // <= 15%
    assertThat(tier(CFR, 30, 1)).contains(Tier.ALTO);
    assertThat(tier(CFR, 45, 1)).contains(Tier.MEDIO);
    assertThat(tier(CFR, 60, 1)).contains(Tier.BAIXO); // > 45%
  }

  @Test
  void mttrTiers() {
    assertThat(tier(MTTR, 0.5, 1)).contains(Tier.ELITE); // < 1h
    assertThat(tier(MTTR, 10, 1)).contains(Tier.ALTO);
    assertThat(tier(MTTR, 100, 1)).contains(Tier.MEDIO);
    assertThat(tier(MTTR, 200, 1)).contains(Tier.BAIXO);
  }

  @Test
  void deploymentFrequencyTierIsFrequencyIndependent() {
    // Same underlying rate of ~1 deploy/day: 1 in a 1-day bucket, 5 in a 5-day week, 20 in a
    // 20-workday month — all Elite because DF is normalized to deploys/day.
    assertThat(tier(DEPLOY_FREQ, 1, 1)).contains(Tier.ELITE);
    assertThat(tier(DEPLOY_FREQ, 5, 5)).contains(Tier.ELITE);
    assertThat(tier(DEPLOY_FREQ, 20, 20)).contains(Tier.ELITE);
    // ~1 deploy/week (0.14/day) is High, independent of bucket length.
    assertThat(tier(DEPLOY_FREQ, 1, 7)).contains(Tier.ALTO);
    assertThat(tier(DEPLOY_FREQ, 4, 28)).contains(Tier.ALTO);
    // ~1/month is Medium; near zero is Low.
    assertThat(tier(DEPLOY_FREQ, 1, 30)).contains(Tier.MEDIO);
    assertThat(tier(DEPLOY_FREQ, 0, 30)).contains(Tier.BAIXO);
  }

  @Test
  void nonDoraMetricHasNoTier() {
    MetricDefinition throughput =
        new MetricDefinition(
            "throughput",
            "Throughput",
            "fluxo",
            EventType.PR,
            AttributionScope.PERSON,
            Aggregation.SUM,
            "PRs",
            Direction.HIGHER_BETTER);
    assertThat(throughput.tierBands()).isEmpty();
    assertThat(Benchmark.classify(throughput, 42, 7)).isEmpty();
  }

  private static java.util.Optional<Tier> tier(MetricDefinition def, double value, int bucketDays) {
    return Benchmark.classify(def, value, bucketDays);
  }
}
