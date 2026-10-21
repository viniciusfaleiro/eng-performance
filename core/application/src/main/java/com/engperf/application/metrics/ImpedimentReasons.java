package com.engperf.application.metrics;

import java.util.List;

/**
 * The composition of a population's waiting time, plus how much of that population carries an
 * impediment signal at all.
 *
 * <p>{@code waitingHours} is the figure the slices add up to — the waiting shown on the board once
 * {@link #scaledTo(double)} has been applied. {@code populationHours} keeps the raw sum over the
 * population, which is a different question and is shown, when it is shown, labelled as context.
 * Reporting the raw sum as the slices' total is what put a drawer of twenty thousand hours next to
 * a board reading zero, leaving the reader with no way to tell which number was about their team.
 *
 * <p>Coverage travels with both because a team that never marks impediments and a team that is
 * never blocked produce the same breakdown, and only one of those readings is good news.
 */
public record ImpedimentReasons(
    List<ImpedimentSlice> slices,
    double waitingHours,
    double populationHours,
    int items,
    int itemsWithSignal) {

  public ImpedimentReasons {
    slices = List.copyOf(slices);
  }

  public static ImpedimentReasons none() {
    return new ImpedimentReasons(List.of(), 0, 0, 0, 0);
  }

  /**
   * The same composition, with the hours rescaled so the slices add up to {@code waitingShown} —
   * the waiting figure the reader clicked. The shares are untouched: they are what the composition
   * is, and they are what the rescaling preserves.
   */
  public ImpedimentReasons scaledTo(double waitingShown) {
    if (populationHours <= 0 || waitingShown <= 0) {
      return new ImpedimentReasons(List.of(), 0, populationHours, items, itemsWithSignal);
    }
    double factor = waitingShown / populationHours;
    return new ImpedimentReasons(
        slices.stream().map(s -> s.withHours(s.hours() * factor)).toList(),
        waitingShown,
        populationHours,
        items,
        itemsWithSignal);
  }
}
