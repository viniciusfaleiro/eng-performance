package com.engperf.domain.metrics;

/**
 * How a metric's events occupy time, which is what decides whether an event belongs to a period.
 *
 * <p>Two different questions were being answered by one rule. A commit, a deploy, a closed pull
 * request and a completed work item all happen <em>at a moment</em>, and the period containing that
 * moment is the period they belong to. A work item in progress does not happen at a moment — it
 * occupies a stretch of time, and every period that stretch touches contains it.
 *
 * <p>Forcing the second into the first is what made WIP report the wrong thing: an item's record
 * carries the date it was last changed, so an item left in progress and untouched fell outside
 * every recent period, and the metric that exists to expose stalled work became blind to exactly
 * the work that had stalled.
 */
public enum Occupation {

  /** The event belongs to the period containing its own date. The default for every metric. */
  INSTANT(null),

  /**
   * The event belongs to every period the stretches it was <em>being worked on</em> overlap, and
   * its own date decides nothing.
   *
   * <p>A consequence to state wherever such a number is shown: it is <strong>not additive across
   * periods</strong>. One item in progress on three days of a week is counted once in the week and
   * once in each of the three days, so the week is not the sum of its days. It also grows with the
   * length of the period, so two periods of different lengths are not comparable.
   */
  IN_PROGRESS("spans"),

  /**
   * The event belongs to every period the stretch it was <strong>committed and not
   * delivered</strong> overlaps — queue included, impediment excluded.
   *
   * <p>An item can occupy time in more than one way, and which one a metric means is a product
   * decision, not a detail. "What was this person working on?" and "what has the team taken on and
   * not finished?" are different stretches of the same item, and while a single interval served
   * both, the second silently inherited the boundaries of the first: work parked in a delivery
   * queue disappeared from the figure that exists to expose it.
   */
  COMMITTED("committed_spans");

  private final String detailKey;

  Occupation(String detailKey) {
    this.detailKey = detailKey;
  }

  /** Which recorded stretch of the event this occupation reads; {@code null} for an instant. */
  public String detailKey() {
    return detailKey;
  }
}
