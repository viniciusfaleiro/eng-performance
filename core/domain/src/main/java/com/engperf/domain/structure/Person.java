package com.engperf.domain.structure;

import com.engperf.domain.common.ConflictException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bottom level of the hierarchy. A Person belongs to exactly one team at a time through an open
 * {@link TeamMembership}; moving teams preserves history (as-of-event). Immutable — mutating
 * operations return a new instance.
 */
public final class Person {

  private final String id;
  private final String name;
  private final String email;
  private final List<TeamMembership> memberships;

  public Person(String id, String name, String email, List<TeamMembership> memberships) {
    this.id = Validation.text(id, "person id");
    this.name = Validation.text(name, "person name");
    this.email = Validation.optional(email);
    List<TeamMembership> copy =
        new ArrayList<>(Objects.requireNonNull(memberships, "memberships must not be null"));
    if (copy.stream().filter(TeamMembership::isOpen).count() > 1) {
      throw new ConflictException("person must have at most one open membership");
    }
    this.memberships = List.copyOf(copy);
  }

  /** Creates a person with a single open membership starting on {@code effectiveDate}. */
  public static Person create(
      String id, String name, String email, String teamId, LocalDate effectiveDate) {
    Objects.requireNonNull(effectiveDate, "effective date must not be null");
    return new Person(id, name, email, List.of(new TeamMembership(teamId, effectiveDate, null)));
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public Optional<String> email() {
    return Optional.ofNullable(email);
  }

  public List<TeamMembership> memberships() {
    return memberships;
  }

  public Optional<String> currentTeamId() {
    return memberships.stream()
        .filter(TeamMembership::isOpen)
        .map(TeamMembership::teamId)
        .findFirst();
  }

  /**
   * Moves the person to a new team effective on the given date: closes the open membership on the
   * day before and opens a new one, keeping past periods on the previous team.
   */
  public Person moveToTeam(String newTeamId, LocalDate effectiveDate) {
    Validation.text(newTeamId, "team id");
    Objects.requireNonNull(effectiveDate, "effective date must not be null");
    List<TeamMembership> next = new ArrayList<>();
    for (TeamMembership m : memberships) {
      if (m.isOpen()) {
        if (!effectiveDate.isAfter(m.start())) {
          throw new ConflictException("effective date must be after the current membership start");
        }
        next.add(new TeamMembership(m.teamId(), m.start(), effectiveDate.minusDays(1)));
      } else {
        next.add(m);
      }
    }
    next.add(new TeamMembership(newTeamId, effectiveDate, null));
    return new Person(id, name, email, next);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Person other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Person[id=" + id + ", name=" + name + "]";
  }
}
