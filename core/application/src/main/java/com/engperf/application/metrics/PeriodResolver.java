package com.engperf.application.metrics;

import com.engperf.application.port.inbound.PeriodRequest;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * The only place that decides which period a request is about.
 *
 * <p>The clock stays the source of "now" — pinning it (as the demo environment does with {@code
 * METRICS_REFERENCE_DATE}) pins the default period, the anchor of a rolling window, and what counts
 * as the future. The browser never computes any of the three: its clock can be minutes or a day
 * off, and a card labelled with a period the engine never used is worse than a slow one.
 */
public final class PeriodResolver implements PeriodResolverUseCase {

  private final Clock clock;

  public PeriodResolver(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Period resolve(Frequency frequency, String anyDateInside) {
    return resolve(PeriodRequest.bucket(frequency, anyDateInside));
  }

  @Override
  public Period resolve(PeriodRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    LocalDate today = LocalDate.now(clock);
    Period period = interpret(request, today);
    period.requireNotFuture(today);
    return period;
  }

  private Period interpret(PeriodRequest request, LocalDate today) {
    boolean hasFrom = present(request.from());
    boolean hasTo = present(request.to());
    if (hasFrom != hasTo) {
      // Half a range is not a narrower question, it is an unanswerable one: guessing the missing
      // edge would silently show a period nobody asked for.
      throw new IllegalArgumentException(
          "intervalo precisa de início e fim; recebi apenas " + (hasFrom ? "o início" : "o fim"));
    }
    if (hasFrom) {
      return Period.between(
          request.frequency(), day(request.from(), "início"), day(request.to(), "fim"));
    }
    if (request.windowDays() != null) {
      LocalDate anchor = present(request.anchor()) ? day(request.anchor(), "âncora") : today;
      return Period.lastDays(request.frequency(), request.windowDays(), anchor);
    }
    return present(request.anchor())
        ? Period.of(request.frequency(), day(request.anchor(), "período"))
        : Period.current(request.frequency(), today);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static LocalDate day(String value, String what) {
    try {
      return LocalDate.parse(value.strip());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "data de " + what + " inválida: '" + value + "' (esperado AAAA-MM-DD)", e);
    }
  }
}
