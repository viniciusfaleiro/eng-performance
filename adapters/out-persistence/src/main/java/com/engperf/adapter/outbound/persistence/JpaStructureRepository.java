package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.TeamMembership;
import com.engperf.domain.structure.Vertical;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed outbound adapter implementing {@link StructureRepositoryPort}. Maps between the
 * framework-free domain and the JPA entities; the inner layers never see JPA.
 */
@Component
@Transactional
public class JpaStructureRepository implements StructureRepositoryPort {

  private final VerticalJpaRepository verticals;
  private final TeamJpaRepository teams;
  private final PersonJpaRepository people;
  private final RepositoryJpaRepository repositories;
  private final CommitterIdentityJpaRepository identities;

  public JpaStructureRepository(
      VerticalJpaRepository verticals,
      TeamJpaRepository teams,
      PersonJpaRepository people,
      RepositoryJpaRepository repositories,
      CommitterIdentityJpaRepository identities) {
    this.verticals = verticals;
    this.teams = teams;
    this.people = people;
    this.repositories = repositories;
    this.identities = identities;
  }

  @Override
  public Vertical saveVertical(Vertical vertical) {
    verticals.save(new VerticalEntity(vertical.id(), vertical.name(), vertical.managerId()));
    return vertical;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Vertical> findVerticals() {
    return verticals.findAll().stream().map(JpaStructureRepository::toVertical).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Vertical> findVertical(String id) {
    return verticals.findById(id).map(JpaStructureRepository::toVertical);
  }

  @Override
  public void deleteVertical(String id) {
    verticals.deleteById(id);
  }

  @Override
  public Team saveTeam(Team team) {
    teams.save(
        new TeamEntity(
            team.id(),
            team.name(),
            team.verticalId(),
            team.managerId(),
            team.productionStageOverride()));
    return team;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Team> findTeams() {
    return teams.findAll().stream().map(JpaStructureRepository::toTeam).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Team> findTeam(String id) {
    return teams.findById(id).map(JpaStructureRepository::toTeam);
  }

  @Override
  public void deleteTeam(String id) {
    teams.deleteById(id);
  }

  @Override
  public Person savePerson(Person person) {
    List<MembershipEmbeddable> memberships =
        person.memberships().stream()
            .map(m -> new MembershipEmbeddable(m.teamId(), m.start(), m.end()))
            .toList();
    people.save(
        new PersonEntity(person.id(), person.name(), person.email().orElse(null), memberships));
    return person;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Person> findPeople() {
    return people.findAll().stream().map(JpaStructureRepository::toPerson).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Person> findPerson(String id) {
    return people.findById(id).map(JpaStructureRepository::toPerson);
  }

  @Override
  public void deletePerson(String id) {
    people.deleteById(id);
  }

  @Override
  public Repository saveRepository(Repository repository) {
    repositories.save(
        new RepositoryEntity(repository.key(), repository.project(), repository.teamId()));
    return repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Repository> findRepositories() {
    return repositories.findAll().stream().map(JpaStructureRepository::toRepository).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Repository> findRepository(String key) {
    return repositories.findById(key).map(JpaStructureRepository::toRepository);
  }

  @Override
  public CommitterIdentity saveIdentity(CommitterIdentity identity) {
    identities.save(
        new CommitterIdentityEntity(
            identity.identity(),
            identity.displayName(),
            identity.personId(),
            identity.commitCount()));
    return identity;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommitterIdentity> findIdentities() {
    return identities.findAll().stream().map(JpaStructureRepository::toIdentity).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CommitterIdentity> findIdentity(String identity) {
    return identities.findById(identity).map(JpaStructureRepository::toIdentity);
  }

  private static Vertical toVertical(VerticalEntity e) {
    return new Vertical(e.getId(), e.getName(), e.getManagerId());
  }

  private static Team toTeam(TeamEntity e) {
    return new Team(
        e.getId(),
        e.getName(),
        e.getVerticalId(),
        e.getManagerId(),
        e.getProductionStageOverride());
  }

  private static Person toPerson(PersonEntity e) {
    List<TeamMembership> memberships =
        e.getMemberships().stream()
            .map(m -> new TeamMembership(m.getTeamId(), m.getStartDate(), m.getEndDate()))
            .toList();
    return new Person(e.getId(), e.getName(), e.getEmail(), memberships);
  }

  private static Repository toRepository(RepositoryEntity e) {
    return new Repository(e.getRepoKey(), e.getProject(), e.getTeamId());
  }

  private static CommitterIdentity toIdentity(CommitterIdentityEntity e) {
    return new CommitterIdentity(
        e.getIdentity(), e.getDisplayName(), e.getPersonId(), e.getCommitCount());
  }
}
