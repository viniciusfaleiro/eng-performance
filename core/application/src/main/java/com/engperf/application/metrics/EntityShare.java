package com.engperf.application.metrics;

/**
 * One entity's contribution to a metric that counts distinct entities rather than events.
 *
 * <p>Carries both counts, not only the ratio: a person with 1 of 2 commits AI-assisted and one with
 * 40 of 80 share the same 50%, and the reader needs to tell them apart before drawing any
 * conclusion about either.
 *
 * @param entityId the person (or team) the events were attributed to
 * @param label the entity's display name — resolved here so the web adapter never has to reach for
 *     the structure repository, which is an outbound port and none of its business
 * @param matching events that met the metric's condition — for AI adoption, the AI-assisted commits
 * @param total the entity's events in the period, which is what makes {@code matching} readable
 */
public record EntityShare(String entityId, String label, long matching, long total) {

  /** The same share with a display name attached. */
  EntityShare withLabel(String name) {
    return new EntityShare(entityId, name, matching, total);
  }

  /** Fraction in {@code [0,1]}; zero when the entity produced nothing (it is then not listed). */
  public double share() {
    return total == 0 ? 0 : (double) matching / total;
  }
}
