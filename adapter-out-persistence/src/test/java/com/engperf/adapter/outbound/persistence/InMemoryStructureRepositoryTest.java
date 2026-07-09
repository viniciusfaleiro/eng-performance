package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InMemoryStructureRepositoryTest {

  private final InMemoryStructureRepository repo = new InMemoryStructureRepository();

  @Test
  void roundTripsEveryEntity() {
    repo.saveVertical(new Vertical("v:pagamentos", "Pagamentos", null));
    repo.saveTeam(new Team("t:checkout", "Checkout", "v:pagamentos", null, null));
    repo.savePerson(Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1)));
    repo.saveRepository(new Repository("checkout-service", "Pagamentos", "t:checkout"));
    repo.saveIdentity(new CommitterIdentity("ana@x.com", "Ana", "p:ana", 10));

    assertThat(repo.findVertical("v:pagamentos")).isPresent();
    assertThat(repo.findTeams()).hasSize(1);
    assertThat(repo.findPerson("p:ana")).get().extracting(Person::name).isEqualTo("Ana");
    assertThat(repo.findRepository("checkout-service"))
        .get()
        .extracting(Repository::teamId)
        .isEqualTo("t:checkout");
    assertThat(repo.findIdentities())
        .singleElement()
        .extracting(CommitterIdentity::isMapped)
        .isEqualTo(true);
  }

  @Test
  void preservesInsertionOrderAndDeletes() {
    repo.saveVertical(new Vertical("v:a", "A", null));
    repo.saveVertical(new Vertical("v:b", "B", null));
    assertThat(repo.findVerticals()).extracting(Vertical::id).containsExactly("v:a", "v:b");
    repo.deleteVertical("v:a");
    assertThat(repo.findVerticals()).extracting(Vertical::id).containsExactly("v:b");
  }
}
