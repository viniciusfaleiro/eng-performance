package com.engperf.domain.structure;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A dated link of a {@link Person} to a {@link Team}. The membership is "open" (the current team)
 * while {@code end} is {@code null}. History is preserved as-of-event: past periods keep the team
 * of record even after the person moves.
 */
public record TeamMembership(String teamId, LocalDate start, LocalDate end) {

  public TeamMembership {
    teamId = Validation.text(teamId, "team id");
    Objects.requireNonNull(start, "start date must not be null");
    if (end != null && end.isBefore(start)) {
      throw new IllegalArgumentException("membership end must not be before start");
    }
  }

  public boolean isOpen() {
    return end == null;
  }
}
