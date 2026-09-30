package com.engperf.application.port.inbound;

import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.Period;

/**
 * Turns "which period is being viewed" into a {@link Period}, in one place.
 *
 * <p>Every screen asks the same question and it has to get the same answer: cards, trends, the
 * heatmap, the individual panel and drilldowns must agree on the exact interval, or a screen ends
 * up describing two different months at once. Rounding a date to its bucket in each controller
 * would be five chances to round it differently.
 */
public interface PeriodResolverUseCase {

  /**
   * The period containing {@code anyDateInside} at {@code frequency}; a blank date means the period
   * containing the present.
   *
   * @throws IllegalArgumentException if the period has not started yet — distinct from a past
   *     period that simply has no events, which is a legitimate zero
   */
  Period resolve(Frequency frequency, String anyDateInside);

  /**
   * The period described by {@code request} — a calendar bucket, a freely chosen range, or a
   * rolling window. The three are decided here, once, so no caller has to know which one it
   * received.
   *
   * @throws IllegalArgumentException if the range is inverted, incomplete, unparseable, or has not
   *     started yet
   */
  Period resolve(PeriodRequest request);
}
