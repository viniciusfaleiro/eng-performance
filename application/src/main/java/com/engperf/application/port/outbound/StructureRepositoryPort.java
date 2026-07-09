package com.engperf.application.port.outbound;

import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.util.List;
import java.util.Optional;

/** Outbound port: persistence for the organization cadastro. Implemented by an outbound adapter. */
public interface StructureRepositoryPort {

  Vertical saveVertical(Vertical vertical);

  List<Vertical> findVerticals();

  Optional<Vertical> findVertical(String id);

  void deleteVertical(String id);

  Team saveTeam(Team team);

  List<Team> findTeams();

  Optional<Team> findTeam(String id);

  void deleteTeam(String id);

  Person savePerson(Person person);

  List<Person> findPeople();

  Optional<Person> findPerson(String id);

  void deletePerson(String id);

  Repository saveRepository(Repository repository);

  List<Repository> findRepositories();

  Optional<Repository> findRepository(String key);

  CommitterIdentity saveIdentity(CommitterIdentity identity);

  List<CommitterIdentity> findIdentities();

  Optional<CommitterIdentity> findIdentity(String identity);
}
