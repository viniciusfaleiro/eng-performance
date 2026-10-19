package com.engperf.application.metrics;

import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;

/**
 * Which metrics a frequency can honestly show.
 *
 * <p>One place, asked by every surface that lists metrics — the Fluxo cards, the comparison heatmap
 * — so the exception does not become a list each of them keeps for itself and forgets to update.
 */
final class MetricAvailability {

  private MetricAvailability() {}

  /**
   * Whether a metric can be read at this frequency.
   *
   * <p>WIP is the exception, and the reason is its own arithmetic: membership is decided by
   * overlap, so the same item counts in every period it crosses. The days of a week do not add up
   * to the week and a month necessarily counts more items than a day — a figure that cannot be
   * summed or compared across lengths invites exactly those two readings whenever it sits on screen
   * beside figures that can. Offering it only at the daily frequency is what removes the
   * invitation.
   *
   * <p>Decided here, in the catalog, so every surface that lists metrics asks the same question
   * instead of each one keeping its own list of exceptions.
   */
  static boolean readableAt(MetricDefinition def, Frequency frequency) {
    return !"wip".equals(def.key()) || frequency == Frequency.DAILY;
  }
}
