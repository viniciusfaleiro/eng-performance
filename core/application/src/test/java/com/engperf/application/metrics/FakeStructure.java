package com.engperf.application.metrics;

import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory {@link StructureRepositoryPort} shared by the metrics service tests. */
final class FakeStructure implements StructureRepositoryPort {
  final List<Vertical> verticals = new ArrayList<>();
  final List<Team> teams = new ArrayList<>();
  final List<Person> people = new ArrayList<>();
  final List<Repository> repositories = new ArrayList<>();
  final List<CommitterIdentity> identities = new ArrayList<>();

  @Override
  public Vertical saveVertical(Vertical v) {
    return v;
  }

  @Override
  public List<Vertical> findVerticals() {
    return verticals;
  }

  @Override
  public Optional<Vertical> findVertical(String id) {
    return verticals.stream().filter(v -> v.id().equals(id)).findFirst();
  }

  @Override
  public void deleteVertical(String id) {}

  @Override
  public Team saveTeam(Team t) {
    return t;
  }

  @Override
  public List<Team> findTeams() {
    return teams;
  }

  @Override
  public Optional<Team> findTeam(String id) {
    return teams.stream().filter(t -> t.id().equals(id)).findFirst();
  }

  @Override
  public void deleteTeam(String id) {}

  @Override
  public Person savePerson(Person p) {
    return p;
  }

  @Override
  public List<Person> findPeople() {
    return people;
  }

  @Override
  public Optional<Person> findPerson(String id) {
    return people.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  @Override
  public void deletePerson(String id) {}

  @Override
  public Repository saveRepository(Repository r) {
    return r;
  }

  @Override
  public List<Repository> findRepositories() {
    return repositories;
  }

  @Override
  public Optional<Repository> findRepository(String key) {
    return repositories.stream().filter(r -> r.key().equals(key)).findFirst();
  }

  @Override
  public void deleteRepository(String key) {}

  @Override
  public CommitterIdentity saveIdentity(CommitterIdentity c) {
    return c;
  }

  @Override
  public List<CommitterIdentity> findIdentities() {
    return identities;
  }

  @Override
  public Optional<CommitterIdentity> findIdentity(String identity) {
    return identities.stream().filter(c -> c.identity().equals(identity)).findFirst();
  }
}
