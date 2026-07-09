package com.engperf.application.structure;

import com.engperf.application.port.inbound.StructureUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Application service for the organization cadastro. Framework-free; wired by the bootstrap. */
public final class StructureService implements StructureUseCase {

  private final StructureRepositoryPort repository;

  public StructureService(StructureRepositoryPort repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Override
  public TreeNode tree() {
    List<Team> teams = repository.findTeams();
    List<Person> people = repository.findPeople();
    List<TreeNode> verticalNodes = new ArrayList<>();
    for (Vertical vertical : repository.findVerticals()) {
      List<TreeNode> teamNodes = new ArrayList<>();
      for (Team team : teams) {
        if (!team.verticalId().equals(vertical.id())) {
          continue;
        }
        List<TreeNode> personNodes = new ArrayList<>();
        for (Person person : people) {
          if (person.currentTeamId().filter(id -> id.equals(team.id())).isPresent()) {
            personNodes.add(new TreeNode(person.id(), person.name(), "person", List.of()));
          }
        }
        teamNodes.add(new TreeNode(team.id(), team.name(), "team", personNodes));
      }
      verticalNodes.add(new TreeNode(vertical.id(), vertical.name(), "vertical", teamNodes));
    }
    return new TreeNode("all", "Visão geral", "overview", verticalNodes);
  }

  @Override
  public List<Vertical> verticals() {
    return repository.findVerticals();
  }

  @Override
  public List<Team> teams() {
    return repository.findTeams();
  }

  @Override
  public List<Person> people() {
    return repository.findPeople();
  }

  @Override
  public Vertical createVertical(String name, String managerId) {
    validateManager(managerId);
    return repository.saveVertical(new Vertical("v:" + Slug.of(name), name, managerId));
  }

  @Override
  public Vertical setVerticalManager(String verticalId, String managerId) {
    Vertical vertical =
        repository.findVertical(verticalId).orElseThrow(() -> notFound("vertical", verticalId));
    validateManager(managerId);
    return repository.saveVertical(vertical.withManager(managerId));
  }

  @Override
  public Team createTeam(String name, String verticalId, String managerId) {
    repository
        .findVertical(verticalId)
        .orElseThrow(() -> new IllegalArgumentException("vertical not found: " + verticalId));
    validateManager(managerId);
    return repository.saveTeam(new Team("t:" + Slug.of(name), name, verticalId, managerId, null));
  }

  @Override
  public Team setTeamManager(String teamId, String managerId) {
    Team team = repository.findTeam(teamId).orElseThrow(() -> notFound("team", teamId));
    validateManager(managerId);
    return repository.saveTeam(team.withManager(managerId));
  }

  @Override
  public Person createPerson(String name, String email, String teamId, LocalDate effectiveDate) {
    repository
        .findTeam(teamId)
        .orElseThrow(() -> new IllegalArgumentException("team not found: " + teamId));
    return repository.savePerson(
        Person.create("p:" + Slug.of(name), name, email, teamId, effectiveDate));
  }

  @Override
  public Person movePerson(String personId, String teamId, LocalDate effectiveDate) {
    Person person = repository.findPerson(personId).orElseThrow(() -> notFound("person", personId));
    repository
        .findTeam(teamId)
        .orElseThrow(() -> new IllegalArgumentException("team not found: " + teamId));
    return repository.savePerson(person.moveToTeam(teamId, effectiveDate));
  }

  private void validateManager(String managerId) {
    if (managerId == null || managerId.isBlank()) {
      return;
    }
    repository
        .findPerson(managerId.strip())
        .orElseThrow(
            () -> new IllegalArgumentException("manager is not a registered person: " + managerId));
  }

  private static NoSuchElementException notFound(String what, String id) {
    return new NoSuchElementException(what + " not found: " + id);
  }
}
