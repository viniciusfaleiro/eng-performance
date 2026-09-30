package com.engperf.application.port.inbound;

import com.engperf.domain.metrics.Frequency;

/**
 * What a caller said about the period it wants, before any of it is resolved against the clock.
 *
 * <p>Three readings share one shape, and which one applies is decided in a single place rather than
 * in each caller: a calendar bucket (only {@code anchor}, possibly absent), a freely chosen range
 * ({@code from}/{@code to}), and a rolling window ({@code windowDays} ending at {@code anchor}).
 *
 * <p>Dates arrive as text because that is how they arrive over HTTP, and refusing an unparseable
 * one is part of resolving. Nothing here is validated: the period resolver is what turns it into a
 * * period or rejects it.
 */
public record PeriodRequest(
    Frequency frequency, String anchor, String from, String to, Integer windowDays) {

  public static PeriodRequest bucket(Frequency frequency, String anchor) {
    return new PeriodRequest(frequency, anchor, null, null, null);
  }
}
