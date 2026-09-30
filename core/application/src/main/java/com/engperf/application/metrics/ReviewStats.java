package com.engperf.application.metrics;

import java.util.List;

/**
 * A person's code-review contribution: comments made, approvals and rejections given (as the
 * reviewer), and reviews given vs received (received = reviews on the person's own PRs).
 *
 * <p>Each direction also carries the reviews behind the count, so a figure can be checked instead
 * of trusted. The lists may be capped while the counts are not, so a truncated list still says how
 * much it is hiding.
 */
public record ReviewStats(
    int commentsGiven,
    int approvalsGiven,
    int rejectionsGiven,
    int reviewsGiven,
    int reviewsReceived,
    List<ReviewEntry> given,
    List<ReviewEntry> received) {

  public ReviewStats {
    given = List.copyOf(given);
    received = List.copyOf(received);
  }
}
