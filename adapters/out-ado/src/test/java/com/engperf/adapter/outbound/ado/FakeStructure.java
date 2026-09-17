package com.engperf.adapter.outbound.ado;

import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.util.List;
import java.util.Optional;

/** Estrutura com os repositórios que o teste registrar. */
final class FakeStructure implements StructureRepositoryPort {
  private final List<Repository> repos;

  FakeStructure(List<Repository> repos) {
    this.repos = repos;
  }

  @Override
  public List<Repository> findRepositories() {
    return repos;
  }

  @Override
  public Vertical saveVertical(Vertical v) {
    return v;
  }

  @Override
  public List<Vertical> findVerticals() {
    return List.of();
  }

  @Override
  public Optional<Vertical> findVertical(String id) {
    return Optional.empty();
  }

  @Override
  public void deleteVertical(String id) {}

  @Override
  public Team saveTeam(Team t) {
    return t;
  }

  @Override
  public List<Team> findTeams() {
    return List.of();
  }

  @Override
  public Optional<Team> findTeam(String id) {
    return Optional.empty();
  }

  @Override
  public void deleteTeam(String id) {}

  @Override
  public Person savePerson(Person p) {
    return p;
  }

  @Override
  public List<Person> findPeople() {
    return List.of();
  }

  @Override
  public Optional<Person> findPerson(String id) {
    return Optional.empty();
  }

  @Override
  public void deletePerson(String id) {}

  @Override
  public Repository saveRepository(Repository r) {
    return r;
  }

  @Override
  public Optional<Repository> findRepository(String key) {
    return repos.stream().filter(r -> r.key().equals(key)).findFirst();
  }

  @Override
  public void deleteRepository(String key) {}

  @Override
  public CommitterIdentity saveIdentity(CommitterIdentity c) {
    return c;
  }

  @Override
  public List<CommitterIdentity> findIdentities() {
    return List.of();
  }

  @Override
  public Optional<CommitterIdentity> findIdentity(String identity) {
    return Optional.empty();
  }
}
