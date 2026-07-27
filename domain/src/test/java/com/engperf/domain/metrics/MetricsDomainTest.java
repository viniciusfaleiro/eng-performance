package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.engperf.domain.structure.Person;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MetricsDomainTest {

  @Test
  void aggregationsSumMedianRatio() {
    assertThat(Aggregations.sum(new double[] {1, 2, 3})).isEqualTo(6.0);
    assertThat(Aggregations.median(new double[] {2, 8})).isEqualTo(5.0); // even
    assertThat(Aggregations.median(new double[] {10, 2, 2, 2, 2})).isEqualTo(2.0); // odd, unsorted
    assertThat(Aggregations.median(new double[] {})).isEqualTo(0.0);
    assertThat(Aggregations.ratio(3, 10)).isEqualTo(0.3);
    assertThat(Aggregations.ratio(3, 0)).isEqualTo(0.0);
  }

  @Test
  void medianOverPopulationNotCompositionOfChildren() {
    // Team = person A (2 PRs @ 10h) + person B (8 PRs @ 2h). Median over the 10 real PRs is 2h.
    double[] population = new double[10];
    population[0] = 10;
    population[1] = 10;
    for (int i = 2; i < 10; i++) {
      population[i] = 2;
    }
    assertThat(Aggregations.median(population)).isEqualTo(2.0);
    // Composing children (median of {10} and {2}) would wrongly give 6.0.
    double composed = Aggregations.median(new double[] {10.0, 2.0});
    assertThat(composed).isEqualTo(6.0).isNotEqualTo(Aggregations.median(population));
  }

  @Test
  void weeklyBucketingIsIsoMonday() {
    Frequency w = Frequency.WEEKLY;
    // 2026-01-07 is a Wednesday; its ISO week starts Monday 2026-01-05.
    assertThat(w.bucketStart(LocalDate.of(2026, 1, 7))).isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(w.nextBucketStart(LocalDate.of(2026, 1, 5))).isEqualTo(LocalDate.of(2026, 1, 12));
    assertThat(w.previousBucketStart(LocalDate.of(2026, 1, 5)))
        .isEqualTo(LocalDate.of(2025, 12, 29));
  }

  @Test
  void dailyAndMonthlyBucketing() {
    assertThat(Frequency.DAILY.bucketStart(LocalDate.of(2026, 3, 9)))
        .isEqualTo(LocalDate.of(2026, 3, 9));
    assertThat(Frequency.MONTHLY.bucketStart(LocalDate.of(2026, 3, 9)))
        .isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(Frequency.MONTHLY.nextBucketStart(LocalDate.of(2026, 3, 1)))
        .isEqualTo(LocalDate.of(2026, 4, 1));
  }

  @Test
  void lastBucketsAreChronologicalEndingWithReference() {
    var buckets = Frequency.MONTHLY.lastBuckets(LocalDate.of(2026, 6, 15), 3);
    assertThat(buckets).hasSize(3);
    assertThat(buckets.get(0).start()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(buckets.get(2).start()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(buckets.get(2).contains(LocalDate.of(2026, 6, 15))).isTrue();
  }

  @Test
  void elapsedDaysReflectsPartialVsFullBucket() {
    Frequency w = Frequency.WEEKLY;
    LocalDate monday = LocalDate.of(2026, 1, 5);
    // Reference is Wednesday of that week → 3 days elapsed (Mon,Tue,Wed).
    assertThat(w.elapsedDays(monday, LocalDate.of(2026, 1, 7))).isEqualTo(3);
    // Reference past the week → full 7 days.
    assertThat(w.elapsedDays(monday, LocalDate.of(2026, 2, 1))).isEqualTo(7);
  }

  @Test
  void membershipResolverIsAsOfEvent() {
    Person ana =
        Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1))
            .moveToTeam("t:antifraude", LocalDate.of(2026, 4, 1));
    assertThat(MembershipResolver.teamOn(ana, LocalDate.of(2026, 2, 15))).contains("t:checkout");
    assertThat(MembershipResolver.teamOn(ana, LocalDate.of(2026, 5, 1))).contains("t:antifraude");
    assertThat(MembershipResolver.teamOn(ana, LocalDate.of(2025, 12, 1))).isEmpty();
  }

  @Test
  void sentimentIsGoodByDirectionNotByUpDown() {
    // lower-is-better falling = GOOD even though the number went down
    assertThat(Sentiment.of(40.0, 58.0, Direction.LOWER_BETTER)).isEqualTo(Sentiment.GOOD);
    assertThat(Sentiment.of(58.0, 40.0, Direction.LOWER_BETTER)).isEqualTo(Sentiment.BAD);
    assertThat(Sentiment.of(30.0, 22.0, Direction.HIGHER_BETTER)).isEqualTo(Sentiment.GOOD);
    assertThat(Sentiment.of(22.0, 30.0, Direction.HIGHER_BETTER)).isEqualTo(Sentiment.BAD);
    assertThat(Sentiment.of(5.0, 5.0, Direction.HIGHER_BETTER)).isEqualTo(Sentiment.NEUTRAL);
    assertThat(Sentiment.of(5.0, null, Direction.HIGHER_BETTER)).isEqualTo(Sentiment.NEUTRAL);
  }

  @Test
  void metricValueComputesSignedChangeAndSentiment() {
    MetricValue v = MetricValue.of(30.0, 24.0, Direction.HIGHER_BETTER);
    assertThat(v.value()).isEqualTo(30.0);
    assertThat(v.changePct()).isEqualTo(25.0);
    assertThat(v.sentiment()).isEqualTo(Sentiment.GOOD);
    assertThat(MetricValue.of(30.0, null, Direction.HIGHER_BETTER).changePct()).isNull();
  }

  @Test
  void coveragePercentAndBucketContains() {
    assertThat(new Coverage(9, 10).percent()).isEqualTo(90.0);
    assertThat(new Coverage(9, 10).unattributed()).isEqualTo(1);
    assertThat(new Coverage(0, 0).percent()).isEqualTo(100.0);
    assertThatIllegalArgumentException().isThrownBy(() -> new Coverage(11, 10));
    Bucket b = new Bucket(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 8));
    assertThat(b.contains(LocalDate.of(2026, 1, 7))).isTrue();
    assertThat(b.contains(LocalDate.of(2026, 1, 8))).isFalse();
  }
}
