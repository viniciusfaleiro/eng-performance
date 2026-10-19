package com.engperf.application.metrics;

import java.util.List;

/**
 * The composition of a population's waiting time, plus how much of that population carries an
 * impediment signal at all.
 *
 * <p>{@code waitingHours} is what the shares are shares of — the sum of the population's waiting
 * hours, not the waiting card, which is a median. Coverage travels with it because a team that
 * never marks impediments and a team that is never blocked produce the same breakdown, and only one
 * of those readings is good news.
 */
public record ImpedimentReasons(
    List<ImpedimentSlice> slices, double waitingHours, int items, int itemsWithSignal) {

  public ImpedimentReasons {
    slices = List.copyOf(slices);
  }

  public static ImpedimentReasons none() {
    return new ImpedimentReasons(List.of(), 0, 0, 0);
  }
}
