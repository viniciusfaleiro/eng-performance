package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A aritmética de intervalos é compartilhada pela ingestão (que une dois sinais de impedimento e
 * pergunta quanto deles cobre um estado) e pelo painel individual (que subtrai o tempo impedido do
 * tempo prorrateado). Os casos de borda são onde as duas leituras poderiam discordar da mesma hora.
 */
class IntervalsTest {

  @Test
  void unionMergesOverlappingAndTouchingIntervals() {
    List<Interval> merged =
        Intervals.union(
            List.of(
                new Interval(30, 40),
                new Interval(0, 10),
                new Interval(5, 20),
                new Interval(20, 25)));
    assertThat(merged).containsExactly(new Interval(0, 25), new Interval(30, 40));
  }

  @Test
  void unionKeepsDisjointIntervalsApartAndOrdered() {
    assertThat(Intervals.union(List.of(new Interval(50, 60), new Interval(10, 20))))
        .containsExactly(new Interval(10, 20), new Interval(50, 60));
  }

  @Test
  void unionAbsorbsAContainedInterval() {
    assertThat(Intervals.union(List.of(new Interval(0, 100), new Interval(40, 50))))
        .containsExactly(new Interval(0, 100));
  }

  @Test
  void unionOfIdenticalIntervalsIsOne() {
    assertThat(Intervals.union(List.of(new Interval(10, 20), new Interval(10, 20))))
        .containsExactly(new Interval(10, 20));
  }

  @Test
  void unionOfNothingIsEmpty() {
    assertThat(Intervals.union(List.of())).isEmpty();
    assertThat(Intervals.union(null)).isEmpty();
  }

  @Test
  void anEmptyOrInvertedIntervalIsRejected() {
    assertThatThrownBy(() -> new Interval(10, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Interval(20, 10)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void overlapCountsOnlyWhatFallsInsideTheWindow() {
    List<Interval> blocked = List.of(new Interval(0, 10), new Interval(20, 40));
    assertThat(Intervals.overlapMs(blocked, 5, 30)).isEqualTo(15); // 5..10 + 20..30
    assertThat(Intervals.overlapMs(blocked, 10, 20)).isZero(); // encosta nas duas pontas
    assertThat(Intervals.overlapMs(List.of(), 0, 100)).isZero();
  }

  @Test
  void clipCutsTheIntervalsToTheWindow() {
    assertThat(Intervals.clip(List.of(new Interval(0, 10), new Interval(50, 60)), 5, 55))
        .containsExactly(new Interval(5, 10), new Interval(50, 55));
  }

  @Test
  void subtractingTheMiddleLeavesBothEnds() {
    assertThat(Intervals.subtract(List.of(new Interval(0, 100)), List.of(new Interval(40, 60))))
        .containsExactly(new Interval(0, 40), new Interval(60, 100));
  }

  @Test
  void subtractingTheWholeIntervalLeavesNothing() {
    assertThat(Intervals.subtract(List.of(new Interval(10, 20)), List.of(new Interval(0, 100))))
        .isEmpty();
  }

  @Test
  void subtractingNothingKeepsTheBaseAsItIs() {
    List<Interval> base = List.of(new Interval(0, 10), new Interval(20, 30));
    assertThat(Intervals.subtract(base, List.of())).isEqualTo(base);
  }

  @Test
  void subtractingSeveralHolesFromSeveralIntervals() {
    List<Interval> base = List.of(new Interval(0, 50), new Interval(60, 100));
    List<Interval> holes =
        List.of(new Interval(10, 20), new Interval(45, 70), new Interval(90, 200));
    assertThat(Intervals.subtract(base, holes))
        .containsExactly(new Interval(0, 10), new Interval(20, 45), new Interval(70, 90));
  }

  @Test
  void aHoleThatOnlyTouchesTheEdgeRemovesNothing() {
    assertThat(Intervals.subtract(List.of(new Interval(10, 20)), List.of(new Interval(20, 30))))
        .containsExactly(new Interval(10, 20));
    assertThat(Intervals.subtract(List.of(new Interval(10, 20)), List.of(new Interval(0, 10))))
        .containsExactly(new Interval(10, 20));
  }

  @Test
  void hoursConvertFromMilliseconds() {
    assertThat(new Interval(0, 3_600_000).hours()).isEqualTo(1.0);
  }
}
