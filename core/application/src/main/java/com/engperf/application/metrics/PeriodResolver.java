package com.engperf.application.metrics;

import com.engperf.application.port.inbound.PeriodResolverUseCase;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The only place that decides which period a request is about.
 *
 * <p>The clock stays the source of "now" — pinning it (as the demo environment does with {@code
 * METRICS_REFERENCE_DATE}) pins both the default period and what counts as the future.
 */
public final class PeriodResolver implements PeriodResolverUseCase {

  private final Clock clock;

  public PeriodResolver(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Period resolve(Frequency frequency, String anyDateInside) {
    LocalDate today = LocalDate.now(clock);
    Period period =
        anyDateInside == null || anyDateInside.isBlank()
            ? Period.current(frequency, today)
            : Period.of(frequency, LocalDate.parse(anyDateInside));
    period.requireNotFuture(today);
    return period;
  }
}
