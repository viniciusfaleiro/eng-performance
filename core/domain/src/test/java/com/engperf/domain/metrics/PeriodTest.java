package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * A period is an interval; a calendar bucket is the case of one whose edges coincide with its
 * frequency.
 */
class PeriodTest {

  @Test
  void anyDateInsideResolvesToTheSamePeriod() {
    LocalDate wednesday = LocalDate.of(2026, 7, 15);
    LocalDate friday = LocalDate.of(2026, 7, 17);

    assertThat(Period.of(Frequency.WEEKLY, wednesday))
        .isEqualTo(Period.of(Frequency.WEEKLY, friday))
        .extracting(Period::start)
        .isEqualTo(LocalDate.of(2026, 7, 13)); // segunda da semana ISO

    assertThat(Period.of(Frequency.MONTHLY, wednesday).start()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(Period.of(Frequency.DAILY, wednesday).start()).isEqualTo(wednesday);
  }

  /**
   * The distinction the whole change rests on: the same type answers both, and it is this question
   * — not the type — that decides where behaviour forks.
   */
  @Test
  void aBucketKnowsItIsOneAndAChosenRangeKnowsItIsNot() {
    assertThat(Period.of(Frequency.MONTHLY, LocalDate.of(2026, 7, 15)).isBucket()).isTrue();
    assertThat(Period.of(Frequency.WEEKLY, LocalDate.of(2026, 7, 15)).isBucket()).isTrue();
    assertThat(Period.of(Frequency.DAILY, LocalDate.of(2026, 7, 15)).isBucket()).isTrue();

    Period chosen =
        Period.between(Frequency.MONTHLY, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 6, 27));
    assertThat(chosen.isBucket()).isFalse();
  }

  /** The end a person writes is the day they mean, so it has to be inside the interval. */
  @Test
  void aChosenRangeIncludesTheDayWrittenAsItsEnd() {
    Period chosen =
        Period.between(Frequency.MONTHLY, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 6, 27));

    assertThat(chosen.contains(LocalDate.of(2026, 3, 12))).isTrue();
    assertThat(chosen.contains(LocalDate.of(2026, 6, 27))).isTrue();
    assertThat(chosen.contains(LocalDate.of(2026, 6, 28))).isFalse();
    assertThat(chosen.contains(LocalDate.of(2026, 3, 11))).isFalse();
    assertThat(chosen.end()).isEqualTo(LocalDate.of(2026, 6, 28));
    assertThat(chosen.days()).isEqualTo(108);
  }

  @Test
  void anInvertedRangeIsRefused() {
    assertThatThrownBy(
            () ->
                Period.between(
                    Frequency.MONTHLY, LocalDate.of(2026, 6, 27), LocalDate.of(2026, 3, 12)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("termina antes de começar");
  }

  @Test
  void aChosenRangeInTheFutureIsRefusedTheSameWayABucketIs() {
    LocalDate today = LocalDate.of(2026, 9, 15);

    assertThatThrownBy(
            () ->
                Period.between(
                        Frequency.DAILY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9))
                    .requireNotFuture(today))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ainda não começou");

    // Um intervalo que já começou vale, mesmo que a ponta final ainda não tenha chegado.
    Period.between(Frequency.DAILY, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 9))
        .requireNotFuture(today);
  }

  /** Comparing against a baseline of a different length would compare two different questions. */
  @Test
  void theIntervalBeforeAChosenRangeHasTheSameDuration() {
    Period chosen =
        Period.between(Frequency.MONTHLY, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 6, 27));
    Period before = chosen.previous();

    assertThat(before.days()).isEqualTo(chosen.days());
    assertThat(before.end()).isEqualTo(chosen.start());
    assertThat(before.start()).isEqualTo(LocalDate.of(2025, 11, 24));
    assertThat(before.next()).isEqualTo(chosen);
  }

  /**
   * A rolling window is an ordinary interval whose edges the system computes — which is why it
   * needs nothing new downstream.
   */
  @Test
  void aRollingWindowIsAnIntervalOfExactlyItsLength() {
    LocalDate today = LocalDate.of(2026, 9, 30);

    Period last7 = Period.lastDays(Frequency.WEEKLY, 7, today);
    assertThat(last7.days()).isEqualTo(7);
    assertThat(last7.start()).isEqualTo(LocalDate.of(2026, 9, 24));
    assertThat(last7.contains(today)).isTrue();
    assertThat(last7.isBucket()).isFalse();

    Period last30 = Period.lastDays(Frequency.MONTHLY, 30, LocalDate.of(2026, 9, 29));
    assertThat(last30.days()).isEqualTo(30);
    assertThat(last30.start()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(last30.isBucket()).isFalse();

    // "Últimos 1 dia" e "hoje" são o mesmo intervalo — é por isso que o diário não ganha
    // alternador.
    assertThat(Period.lastDays(Frequency.DAILY, 1, today))
        .isEqualTo(Period.of(Frequency.DAILY, today));

    assertThatThrownBy(() -> Period.lastDays(Frequency.WEEKLY, 0, today))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * On the last day of a 30-day month, the last 30 days <em>are</em> that month — the edges really
   * do coincide, so the honest answer is yes. It costs nothing: the elapsed slice of a window that
   * ends today is its full length either way, and the baseline it compares against is then the
   * month before, which is the reading a person looking at that window would expect anyway.
   */
  @Test
  void aRollingWindowThatLandsExactlyOnACalendarBucketIsOne() {
    Period thirtyDaysToSep30 = Period.lastDays(Frequency.MONTHLY, 30, LocalDate.of(2026, 9, 30));

    assertThat(thirtyDaysToSep30).isEqualTo(Period.of(Frequency.MONTHLY, LocalDate.of(2026, 9, 5)));
    assertThat(thirtyDaysToSep30.isBucket()).isTrue();
  }

  /**
   * The elapsed-slice comparison exists because half a month is not a month. A range someone typed
   * is already exactly what they asked for, so clipping it would answer a different question.
   */
  @Test
  void aChosenRangeEndingTodayIsNotTreatedAsInProgress() {
    LocalDate today = LocalDate.of(2026, 9, 15);

    assertThat(Period.of(Frequency.MONTHLY, today).inProgress(today)).isTrue();
    assertThat(Period.between(Frequency.MONTHLY, LocalDate.of(2026, 8, 3), today).inProgress(today))
        .isFalse();
    assertThat(Period.lastDays(Frequency.WEEKLY, 7, today).inProgress(today)).isFalse();
  }

  @Test
  void switchingFrequencyKeepsAChosenRangeIntact() {
    Period chosen =
        Period.between(Frequency.MONTHLY, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 6, 27));
    Period weekly = chosen.at(Frequency.WEEKLY);

    assertThat(weekly.start()).isEqualTo(chosen.start());
    assertThat(weekly.end()).isEqualTo(chosen.end());
    assertThat(weekly.frequency()).isEqualTo(Frequency.WEEKLY);
  }

  @Test
  void steppingBackAndForwardReturnsToTheStart() {
    Period july = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 7, 10));

    assertThat(july.previous().next()).isEqualTo(july);
    assertThat(july.previous().start()).isEqualTo(LocalDate.of(2026, 6, 1));
  }

  /** Year boundaries are where off-by-one bucket maths usually shows up. */
  @Test
  void steppingCrossesTheYearBoundary() {
    Period january = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 1, 20));
    assertThat(january.previous().start()).isEqualTo(LocalDate.of(2025, 12, 1));

    Period firstWeekOf2026 = Period.of(Frequency.WEEKLY, LocalDate.of(2026, 1, 1));
    assertThat(firstWeekOf2026.previous().end()).isEqualTo(firstWeekOf2026.start());
  }

  @Test
  void boundsAreHalfOpen() {
    Period july = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 7, 1));

    assertThat(july.contains(LocalDate.of(2026, 7, 1))).isTrue();
    assertThat(july.contains(LocalDate.of(2026, 7, 31))).isTrue();
    assertThat(july.end()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(july.contains(july.end())).isFalse();
  }

  @Test
  void inProgressOnlyForThePeriodHoldingToday() {
    LocalDate today = LocalDate.of(2026, 9, 15);

    assertThat(Period.of(Frequency.MONTHLY, today).inProgress(today)).isTrue();
    assertThat(Period.of(Frequency.MONTHLY, LocalDate.of(2026, 7, 1)).inProgress(today)).isFalse();
  }

  /**
   * A future period and an empty past period are different answers: one has no correct value, the
   * other's correct value is zero.
   */
  @Test
  void aFuturePeriodIsRefusedButAPastOneIsNot() {
    LocalDate today = LocalDate.of(2026, 9, 15);

    assertThatThrownBy(
            () -> Period.of(Frequency.MONTHLY, LocalDate.of(2026, 11, 3)).requireNotFuture(today))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ainda não começou");

    Period longPast = Period.of(Frequency.MONTHLY, LocalDate.of(2019, 3, 1));
    longPast.requireNotFuture(today); // não lança
    Period.of(Frequency.MONTHLY, today).requireNotFuture(today); // o corrente também vale
  }

  @Test
  void switchingFrequencyKeepsTheAnchorDay() {
    Period july = Period.of(Frequency.MONTHLY, LocalDate.of(2026, 7, 1));

    assertThat(july.at(Frequency.WEEKLY).start()).isEqualTo(LocalDate.of(2026, 6, 29));
    assertThat(july.at(Frequency.DAILY).start()).isEqualTo(LocalDate.of(2026, 7, 1));
  }
}
