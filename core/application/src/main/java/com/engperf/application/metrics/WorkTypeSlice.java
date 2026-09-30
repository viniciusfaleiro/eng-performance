package com.engperf.application.metrics;

import java.util.List;

/**
 * One work-type slice of a person's effort distribution: the hours, the share of the total, and the
 * items counted under it.
 *
 * <p>{@code itemCount} is the whole count and {@code items} may be capped, so a truncated list
 * still says how much it is hiding. {@code hours} is the sum of the items' counted hours by
 * construction — the slice is built from them rather than from a parallel accumulator, so an opened
 * list can never fail to add up to the figure it opened from.
 */
public record WorkTypeSlice(
    String type,
    String label,
    double hours,
    double sharePct,
    int itemCount,
    List<WorkItemEntry> items) {

  public WorkTypeSlice {
    items = List.copyOf(items);
  }
}
