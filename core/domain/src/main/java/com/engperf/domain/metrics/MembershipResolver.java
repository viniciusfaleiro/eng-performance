package com.engperf.domain.metrics;

import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.TeamMembership;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves the team a Person belonged to on a given date (as-of-event), from their membership
 * history. A move preserves history, so an event dated before a move resolves to the old team.
 */
public final class MembershipResolver {

  private MembershipResolver() {}

  public static Optional<String> teamOn(Person person, LocalDate date) {
    for (TeamMembership m : person.memberships()) {
      boolean startedByThen = !date.isBefore(m.start());
      boolean notYetEnded = m.end() == null || !date.isAfter(m.end());
      if (startedByThen && notYetEnded) {
        return Optional.of(m.teamId());
      }
    }
    return Optional.empty();
  }
}
