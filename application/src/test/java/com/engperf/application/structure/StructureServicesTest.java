package com.engperf.application.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructureServicesTest {

  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUL1 = LocalDate.of(2026, 7, 1);

  private FakeStructureRepository repo;
  private StructureService structure;
  private RepositoryService repositories;
  private IdentityService identities;

  @BeforeEach
  void setUp() {
    repo = new FakeStructureRepository();
    structure = new StructureService(repo);
    repositories = new RepositoryService(repo);
    identities = new IdentityService(repo);
  }

  @Test
  void buildsTreeFromRegisteredStructure() {
    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    structure.createPerson("Ana Souza", "ana@x.com", "t:checkout", JAN1);

    TreeNode root = structure.tree();
    assertThat(root.id()).isEqualTo("all");
    assertThat(root.children())
        .singleElement()
        .satisfies(v -> assertThat(v.id()).isEqualTo("v:pagamentos"));
    TreeNode team = root.children().get(0).children().get(0);
    assertThat(team.level()).isEqualTo("team");
    assertThat(team.children())
        .singleElement()
        .satisfies(p -> assertThat(p.id()).isEqualTo("p:ana-souza"));
  }

  @Test
  void createTeamRejectsMissingVertical() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> structure.createTeam("Checkout", "v:none", null));
  }

  @Test
  void setTeamManagerRejectsUnregisteredPerson() {
    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> structure.setTeamManager("t:checkout", "p:ghost"));
  }

  @Test
  void setTeamManagerAcceptsRegisteredPerson() {
    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    structure.createPerson("Ana Souza", null, "t:checkout", JAN1);
    assertThat(structure.setTeamManager("t:checkout", "p:ana-souza").managerId())
        .isEqualTo("p:ana-souza");
  }

  @Test
  void movePersonPreservesHistory() {
    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    structure.createTeam("Antifraude", "v:pagamentos", null);
    structure.createPerson("Ana Souza", null, "t:checkout", JAN1);

    Person moved = structure.movePerson("p:ana-souza", "t:antifraude", JUL1);
    assertThat(moved.currentTeamId()).contains("t:antifraude");
    assertThat(moved.memberships()).hasSize(2);
  }

  @Test
  void movePersonRejectsMissingPerson() {
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> structure.movePerson("p:ghost", "t:x", JUL1));
  }

  @Test
  void mapRepositoryToTeamAndReject() {
    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    repo.saveRepository(new Repository("checkout-service", "Pagamentos", null));

    assertThat(repositories.mapToTeam("checkout-service", "t:checkout").teamId())
        .isEqualTo("t:checkout");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> repositories.mapToTeam("checkout-service", "t:none"));
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> repositories.mapToTeam("none", "t:checkout"));
  }

  @Test
  void assignIdentityRaisesCoverage() {
    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    structure.createPerson("Ana Souza", null, "t:checkout", JAN1);
    repo.saveIdentity(new CommitterIdentity("ana@x.com", "Ana", null, 60));
    repo.saveIdentity(new CommitterIdentity("bot@ci", "bot", null, 40));

    assertThat(identities.coverage().attributedPercent()).isZero();
    identities.assign("ana@x.com", "p:ana-souza");
    assertThat(identities.coverage().attributedPercent()).isEqualTo(60.0);
    identities.assign("ana@x.com", null);
    assertThat(identities.coverage().attributedPercent()).isZero();
  }
}
