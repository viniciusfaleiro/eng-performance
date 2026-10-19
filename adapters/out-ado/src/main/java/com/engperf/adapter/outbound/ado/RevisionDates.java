package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * When a work item revision happened.
 *
 * <p>Azure DevOps stamps the revision that has not been superseded yet with {@code revisedDate =
 * 9999-01-01} — an "open end date", not a missing one — and {@code System.ChangedDate} stands in
 * for it. Two readings of the same history need that rule (the state transitions and the impediment
 * signal), and two copies of it would be two chances to disagree about when the most recent
 * revision happened, which is exactly the revision that decides whether an item counts as
 * delivered.
 */
final class RevisionDates {

  private RevisionDates() {}

  /** The revision's instant, or {@code null} when it cannot be situated in time. */
  static Instant of(JsonNode update, Instant changed, Instant now) {
    if (!update.hasNonNull("revisedDate")) {
      return null;
    }
    Instant at = AdoMapper.parseInstant(update.path("revisedDate").asText());
    if (at == null) {
      return null;
    }
    if (!at.isAfter(now)) {
      return at;
    }
    return (changed == null || changed.isAfter(now)) ? null : changed;
  }
}
