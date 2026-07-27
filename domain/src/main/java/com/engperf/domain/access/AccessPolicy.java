package com.engperf.domain.access;

import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves an account's {@link AccessScope} from its role and the position of its linked Person in
 * the structure. Pure domain logic — no framework, no persistence.
 *
 * <p>{@code admin} grants configuration + everything; {@code exec} grants org-wide read of
 * aggregates (no individuals, no config). Otherwise the scope is derived from the Person: manager
 * of a team sees the team and its individuals (coaching); manager of a vertical sees the vertical
 * with its teams aggregated; a member sees their own data and their team's aggregate.
 */
public final class AccessPolicy {

  private AccessPolicy() {}

  public static AccessScope scopeOf(
      UserAccount account, List<Vertical> verticals, List<Team> teams, List<Person> people) {
    boolean admin = account.role() == Role.ADMIN;
    boolean orgWide = admin || account.role() == Role.EXEC;
    Set<String> verticalIds = new HashSet<>();
    Set<String> teamIds = new HashSet<>();
    Set<String> personIds = new HashSet<>();

    String personId = account.personId();
    if (personId != null) {
      personIds.add(personId);
      people.stream()
          .filter(p -> p.id().equals(personId))
          .findFirst()
          .flatMap(Person::currentTeamId)
          .ifPresent(teamIds::add);

      for (Team team : teams) {
        if (personId.equals(team.managerId())) {
          teamIds.add(team.id());
          for (Person p : people) {
            if (p.currentTeamId().filter(id -> id.equals(team.id())).isPresent()) {
              personIds.add(p.id());
            }
          }
        }
      }
      for (Vertical vertical : verticals) {
        if (personId.equals(vertical.managerId())) {
          verticalIds.add(vertical.id());
          for (Team team : teams) {
            if (team.verticalId().equals(vertical.id())) {
              teamIds.add(team.id());
            }
          }
        }
      }
    }
    return new AccessScope(admin, orgWide, verticalIds, teamIds, personIds);
  }
}
