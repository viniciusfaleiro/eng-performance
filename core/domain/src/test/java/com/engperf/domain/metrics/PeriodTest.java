package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** A period is a bucket identified by its first day — built from any day inside it. */
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

  @Test
  void aStartThatIsNotTheFirstDayOfItsBucketIsRefused() {
    assertThatThrownBy(() -> new Period(Frequency.MONTHLY, LocalDate.of(2026, 7, 15)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("first day of its bucket");
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
