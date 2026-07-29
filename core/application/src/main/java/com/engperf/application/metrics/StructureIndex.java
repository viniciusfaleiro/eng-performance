package com.engperf.application.metrics;

import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.MembershipResolver;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A read-model snapshot of the structure used to attribute events. Resolves an event to its {@link
 * Attribution} (person/team/vertical) following the metric's scope and the as-of-event membership;
 * an event that does not fully resolve is unattributed.
 */
public final class StructureIndex {

  /**
   * person/team/vertical an event resolved to; {@code personId} is null for repo-scoped metrics.
   */
  public record Attribution(String personId, String teamId, String verticalId) {

    public boolean belongsTo(String nodeId) {
      if (nodeId == null || nodeId.equals("all")) {
        return true;
      }
      if (nodeId.startsWith("v:")) {
        return nodeId.equals(verticalId);
      }
      if (nodeId.startsWith("t:")) {
        return nodeId.equals(teamId);
      }
      if (nodeId.startsWith("p:")) {
        return nodeId.equals(personId);
      }
      return false;
    }

    /** The entity that owns this event for snapshot roll-up (person if known, else team). */
    public String entityKey() {
      return personId != null ? personId : teamId;
    }
  }

  private final Map<String, Person> peopleById = new HashMap<>();
  private final Map<String, String> identityToPerson = new HashMap<>();
  private final Map<String, String> teamToVertical = new HashMap<>();
  private final Map<String, String> repoToTeam = new HashMap<>();

  public StructureIndex(
      List<Person> people,
      List<Team> teams,
      List<Repository> repositories,
      List<CommitterIdentity> identities) {
    people.forEach(p -> peopleById.put(p.id(), p));
    teams.forEach(t -> teamToVertical.put(t.id(), t.verticalId()));
    repositories.forEach(
        r -> {
          if (r.teamId() != null) {
            // Key lower-cased: DEPLOY events carry the repo name as ADO returns it (lower-cased),
            // while registered keys keep their original case — match without regard to case.
            repoToTeam.put(r.key().toLowerCase(Locale.ROOT), r.teamId());
          }
        });
    identities.forEach(
        i -> {
          if (i.personId() != null) {
            identityToPerson.put(i.identity(), i.personId());
          }
        });
  }

  public Optional<Attribution> attribute(RawEvent event, AttributionScope scope) {
    return scope == AttributionScope.PERSON ? attributeByPerson(event) : attributeByRepo(event);
  }

  private Optional<Attribution> attributeByPerson(RawEvent event) {
    if (event.committerIdentity() == null) {
      return Optional.empty();
    }
    String personId = identityToPerson.get(event.committerIdentity());
    if (personId == null) {
      return Optional.empty();
    }
    Person person = peopleById.get(personId);
    if (person == null) {
      return Optional.empty();
    }
    Optional<String> teamId = MembershipResolver.teamOn(person, event.occurredOn());
    if (teamId.isEmpty()) {
      return Optional.empty();
    }
    String verticalId = teamToVertical.get(teamId.get());
    if (verticalId == null) {
      return Optional.empty();
    }
    return Optional.of(new Attribution(personId, teamId.get(), verticalId));
  }

  private Optional<Attribution> attributeByRepo(RawEvent event) {
    if (event.repoKey() == null) {
      return Optional.empty();
    }
    String teamId = repoToTeam.get(event.repoKey().toLowerCase(Locale.ROOT));
    if (teamId == null) {
      return Optional.empty();
    }
    String verticalId = teamToVertical.get(teamId);
    if (verticalId == null) {
      return Optional.empty();
    }
    return Optional.of(new Attribution(null, teamId, verticalId));
  }
}
