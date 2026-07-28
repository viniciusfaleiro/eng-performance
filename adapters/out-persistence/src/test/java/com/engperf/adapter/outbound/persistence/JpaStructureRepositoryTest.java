package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test against a real PostgreSQL provisioned by Testcontainers. Flyway builds the
 * schema; the entity mappings are validated against it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaStructureRepository.class)
@Testcontainers
class JpaStructureRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JpaStructureRepository repo;

  @Test
  void roundTripsEveryEntityAgainstPostgres() {
    repo.saveVertical(new Vertical("v:pagamentos", "Pagamentos", "p:ana"));
    repo.saveTeam(new Team("t:checkout", "Checkout", "v:pagamentos", "p:ana", null));
    repo.savePerson(
        Person.create("p:ana", "Ana", "ana@x.com", "t:checkout", LocalDate.of(2026, 1, 1)));
    repo.saveRepository(
        new Repository("checkout-service", "org", "Pagamentos", "t:checkout", null));
    repo.saveIdentity(new CommitterIdentity("ana@x.com", "Ana", "p:ana", 42));

    assertThat(repo.findVertical("v:pagamentos"))
        .get()
        .extracting(Vertical::name)
        .isEqualTo("Pagamentos");
    assertThat(repo.findTeam("t:checkout"))
        .get()
        .extracting(Team::verticalId)
        .isEqualTo("v:pagamentos");
    assertThat(repo.findPerson("p:ana"))
        .get()
        .extracting(p -> p.currentTeamId().orElse(null))
        .isEqualTo("t:checkout");
    assertThat(repo.findRepository("checkout-service"))
        .get()
        .extracting(Repository::isMapped)
        .isEqualTo(true);
    assertThat(repo.findIdentities())
        .singleElement()
        .extracting(CommitterIdentity::isMapped)
        .isEqualTo(true);
  }

  @Test
  void personMembershipHistoryIsPersisted() {
    repo.saveVertical(new Vertical("v:p", "P", null));
    repo.saveTeam(new Team("t:a", "A", "v:p", null, null));
    repo.saveTeam(new Team("t:b", "B", "v:p", null, null));
    Person moved =
        Person.create("p:x", "X", null, "t:a", LocalDate.of(2026, 1, 1))
            .moveToTeam("t:b", LocalDate.of(2026, 7, 1));
    repo.savePerson(moved);

    Person loaded = repo.findPerson("p:x").orElseThrow();
    assertThat(loaded.currentTeamId()).contains("t:b");
    assertThat(loaded.memberships()).hasSize(2);
  }

  @Test
  void unmappedRepositoryAndIdentityHaveNoOwner() {
    repo.saveRepository(new Repository("legacy", "org", "Plataforma", null, null));
    repo.saveIdentity(new CommitterIdentity("bot@ci", "bot", null, 10));

    assertThat(repo.findRepository("legacy"))
        .get()
        .extracting(Repository::isMapped)
        .isEqualTo(false);
    assertThat(repo.findIdentity("bot@ci"))
        .get()
        .extracting(CommitterIdentity::isMapped)
        .isEqualTo(false);
  }
}
