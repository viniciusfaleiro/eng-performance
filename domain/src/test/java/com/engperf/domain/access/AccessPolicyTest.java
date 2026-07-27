package com.engperf.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

  private static final LocalDate D = LocalDate.of(2026, 1, 1);

  private static final List<Vertical> VERTICALS =
      List.of(
          new Vertical("v:pag", "Pagamentos", "p:ana"),
          new Vertical("v:plat", "Plataforma", "p:pvm"));
  private static final List<Team> TEAMS =
      List.of(
          new Team("t:checkout", "Checkout", "v:pag", "p:ana", null),
          new Team("t:antifraude", "Antifraude", "v:pag", "p:carla", null),
          new Team("t:core", "Core", "v:plat", null, null));
  private static final List<Person> PEOPLE =
      List.of(
          Person.create("p:ana", "Ana", null, "t:checkout", D),
          Person.create("p:bruno", "Bruno", null, "t:checkout", D),
          Person.create("p:carla", "Carla", null, "t:antifraude", D),
          Person.create("p:diego", "Diego", null, "t:antifraude", D),
          Person.create("p:pvm", "PVM", null, "t:core", D),
          Person.create("p:eduardo", "Eduardo", null, "t:core", D));

  private static AccessScope scope(Role role, String personId) {
    return AccessPolicy.scopeOf(
        new UserAccount("u:x", "X", "x@x.com", role, AccountStatus.ACTIVE, personId, "h"),
        VERTICALS,
        TEAMS,
        PEOPLE);
  }

  @Test
  void adminSeesEverythingAndConfig() {
    AccessScope s = scope(Role.ADMIN, null);
    assertThat(s.canConfigure()).isTrue();
    assertThat(s.canView("all")).isTrue();
    assertThat(s.canView("v:plat")).isTrue();
    assertThat(s.canView("t:core")).isTrue();
    assertThat(s.canViewIndividual("p:eduardo")).isTrue();
  }

  @Test
  void execReadsOrgWideAggregatesButNoConfigNorIndividuals() {
    AccessScope s = scope(Role.EXEC, null);
    assertThat(s.canConfigure()).isFalse();
    assertThat(s.canView("all")).isTrue();
    assertThat(s.canView("v:pag")).isTrue();
    assertThat(s.canView("t:checkout")).isTrue();
    assertThat(s.canViewIndividual("p:bruno")).isFalse();
  }

  @Test
  void teamManagerSeesTeamAndItsPeopleOnly() {
    AccessScope s = scope(Role.MANAGER, "p:carla"); // manages t:antifraude
    assertThat(s.canConfigure()).isFalse();
    assertThat(s.canView("t:antifraude")).isTrue();
    assertThat(s.canViewIndividual("p:diego")).isTrue(); // coaching own team
    assertThat(s.canViewIndividual("p:carla")).isTrue(); // self
    assertThat(s.canView("t:checkout")).isFalse();
    assertThat(s.canView("all")).isFalse();
  }

  @Test
  void verticalManagerSeesVerticalAggregatedNoIndividuals() {
    AccessScope s = scope(Role.MANAGER, "p:pvm"); // manages vertical v:plat, member of t:core
    assertThat(s.canView("v:plat")).isTrue();
    assertThat(s.canView("t:core")).isTrue(); // aggregate
    assertThat(s.canViewIndividual("p:eduardo")).isFalse(); // vertical mgr does not see individuals
    assertThat(s.canViewIndividual("p:pvm")).isTrue(); // own
    assertThat(s.canView("v:pag")).isFalse();
  }

  @Test
  void memberSeesOwnDataAndTeamAggregateOnly() {
    AccessScope s = scope(Role.CONTRIBUTOR, "p:bruno"); // member of t:checkout
    assertThat(s.canView("t:checkout")).isTrue();
    assertThat(s.canViewIndividual("p:bruno")).isTrue();
    assertThat(s.canViewIndividual("p:ana")).isFalse(); // teammate — coaching-only
    assertThat(s.canView("t:antifraude")).isFalse();
    assertThat(s.canView("all")).isFalse();
    assertThat(s.canConfigure()).isFalse();
  }

  @Test
  void managerWithoutCommitsStillGetsScope() {
    // p:head is a pure manager id: not a team member, not in PEOPLE, and (by construction, since
    // AccessPolicy takes no committer identities at all) has no commits. Scope must still resolve
    // from the structure alone. Mirrors the seeded managers with no committer identity.
    List<Vertical> verts = List.of(new Vertical("v:g", "Growth", "p:head"));
    List<Team> teams =
        List.of(
            new Team("t:aq", "Aquisicao", "v:g", "p:head", null),
            new Team("t:rt", "Retencao", "v:g", null, null));
    List<Person> people = List.of(Person.create("p:julia", "Julia", null, "t:aq", D));
    AccessScope s =
        AccessPolicy.scopeOf(
            new UserAccount(
                "u:h", "H", "h@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:head", "h"),
            verts,
            teams,
            people);
    assertThat(s.canView("v:g")).isTrue(); // manages the vertical
    assertThat(s.canView("t:aq")).isTrue();
    assertThat(s.canView("t:rt")).isTrue(); // vertical aggregate
    assertThat(s.canViewIndividual("p:julia")).isTrue(); // coaching of the team it manages
    assertThat(s.canViewIndividual("p:head")).isTrue(); // self
  }
}
