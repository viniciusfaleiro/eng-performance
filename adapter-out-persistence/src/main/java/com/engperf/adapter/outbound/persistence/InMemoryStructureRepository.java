package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter backing {@link StructureRepositoryPort} with in-process maps.
 *
 * <p>Placeholder for a real datastore: swapping this for a JPA/ADO-backed adapter requires no
 * change to the domain or application layers. Insertion order is preserved so the structure tree
 * reads deterministically.
 */
@Component
public class InMemoryStructureRepository implements StructureRepositoryPort {

  private final Map<String, Vertical> verticals =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private final Map<String, Team> teams = Collections.synchronizedMap(new LinkedHashMap<>());
  private final Map<String, Person> people = Collections.synchronizedMap(new LinkedHashMap<>());
  private final Map<String, Repository> repositories =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private final Map<String, CommitterIdentity> identities =
      Collections.synchronizedMap(new LinkedHashMap<>());

  @Override
  public Vertical saveVertical(Vertical vertical) {
    verticals.put(vertical.id(), vertical);
    return vertical;
  }

  @Override
  public List<Vertical> findVerticals() {
    return snapshot(verticals);
  }

  @Override
  public Optional<Vertical> findVertical(String id) {
    return Optional.ofNullable(verticals.get(id));
  }

  @Override
  public void deleteVertical(String id) {
    verticals.remove(id);
  }

  @Override
  public Team saveTeam(Team team) {
    teams.put(team.id(), team);
    return team;
  }

  @Override
  public List<Team> findTeams() {
    return snapshot(teams);
  }

  @Override
  public Optional<Team> findTeam(String id) {
    return Optional.ofNullable(teams.get(id));
  }

  @Override
  public void deleteTeam(String id) {
    teams.remove(id);
  }

  @Override
  public Person savePerson(Person person) {
    people.put(person.id(), person);
    return person;
  }

  @Override
  public List<Person> findPeople() {
    return snapshot(people);
  }

  @Override
  public Optional<Person> findPerson(String id) {
    return Optional.ofNullable(people.get(id));
  }

  @Override
  public void deletePerson(String id) {
    people.remove(id);
  }

  @Override
  public Repository saveRepository(Repository repository) {
    repositories.put(repository.key(), repository);
    return repository;
  }

  @Override
  public List<Repository> findRepositories() {
    return snapshot(repositories);
  }

  @Override
  public Optional<Repository> findRepository(String key) {
    return Optional.ofNullable(repositories.get(key));
  }

  @Override
  public CommitterIdentity saveIdentity(CommitterIdentity identity) {
    identities.put(identity.identity(), identity);
    return identity;
  }

  @Override
  public List<CommitterIdentity> findIdentities() {
    return snapshot(identities);
  }

  @Override
  public Optional<CommitterIdentity> findIdentity(String identity) {
    return Optional.ofNullable(identities.get(identity));
  }

  private static <T> List<T> snapshot(Map<String, T> map) {
    synchronized (map) {
      return new ArrayList<>(map.values());
    }
  }
}
