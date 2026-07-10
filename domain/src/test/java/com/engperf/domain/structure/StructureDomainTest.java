package com.engperf.domain.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.engperf.domain.common.ConflictException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructureDomainTest {

  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUL1 = LocalDate.of(2026, 7, 1);

  @Test
  void verticalAndTeamRejectBlankNames() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Vertical("v1", "  ", null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Team("t1", "Checkout", " ", null, null));
  }

  @Test
  void verticalAndTeamNormalizeOptionalFields() {
    assertThat(new Vertical("v1", " Pagamentos ", "  ").managerId()).isNull();
    Team team = new Team("t1", "Checkout", "v1", " p:ana ", " prod ");
    assertThat(team.managerId()).isEqualTo("p:ana");
    assertThat(team.productionStageOverride()).isEqualTo("prod");
    assertThat(team.withManager("p:bruno").managerId()).isEqualTo("p:bruno");
  }

  @Test
  void personHasOneCurrentTeam() {
    Person p = Person.create("p:ana", "Ana", "ana@x.com", "t:checkout", JAN1);
    assertThat(p.currentTeamId()).contains("t:checkout");
    assertThat(p.memberships()).hasSize(1);
    assertThat(p.email()).contains("ana@x.com");
  }

  @Test
  void moveToTeamClosesPriorMembershipAndOpensNew() {
    Person moved =
        Person.create("p:ana", "Ana", null, "t:checkout", JAN1).moveToTeam("t:antifraude", JUL1);

    assertThat(moved.currentTeamId()).contains("t:antifraude");
    assertThat(moved.memberships()).hasSize(2);
    TeamMembership prior =
        moved.memberships().stream().filter(m -> !m.isOpen()).findFirst().orElseThrow();
    assertThat(prior.teamId()).isEqualTo("t:checkout");
    assertThat(prior.end()).isEqualTo(JUL1.minusDays(1));
  }

  @Test
  void moveRejectsEffectiveDateNotAfterCurrentStart() {
    Person p = Person.create("p:ana", "Ana", null, "t:checkout", JUL1);
    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(() -> p.moveToTeam("t:antifraude", JAN1));
  }

  @Test
  void personRejectsMoreThanOneOpenMembership() {
    List<TeamMembership> two =
        List.of(new TeamMembership("t:a", JAN1, null), new TeamMembership("t:b", JUL1, null));
    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(() -> new Person("p:x", "X", null, two));
  }

  @Test
  void membershipRejectsEndBeforeStart() {
    assertThatIllegalArgumentException().isThrownBy(() -> new TeamMembership("t:a", JUL1, JAN1));
  }

  @Test
  void repositoryMapsToAtMostOneTeam() {
    Repository unmapped = new Repository("checkout-service", "Pagamentos", null);
    assertThat(unmapped.isMapped()).isFalse();
    Repository mapped = unmapped.assignTo("t:checkout");
    assertThat(mapped.isMapped()).isTrue();
    assertThat(mapped.teamId()).isEqualTo("t:checkout");
    assertThat(mapped.assignTo("t:antifraude").teamId()).isEqualTo("t:antifraude");
  }

  @Test
  void committerIdentityLinksAndUnlinks() {
    CommitterIdentity id = new CommitterIdentity("ana@x.com", "Ana", null, 40);
    assertThat(id.isMapped()).isFalse();
    assertThat(id.linkTo("p:ana").isMapped()).isTrue();
    assertThat(id.linkTo("p:ana").unlink().personId()).isNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CommitterIdentity("x", null, null, -1));
  }
}
