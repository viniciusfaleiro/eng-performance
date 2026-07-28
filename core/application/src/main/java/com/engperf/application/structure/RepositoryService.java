package com.engperf.application.structure;

import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.Repository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Application service mapping repositories to teams (1 repo → 1 team). */
public final class RepositoryService implements RepositoryUseCase {

  private final StructureRepositoryPort repository;

  public RepositoryService(StructureRepositoryPort repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Override
  public List<Repository> repositories() {
    return repository.findRepositories();
  }

  @Override
  public Repository register(
      String key, String organization, String project, String teamId, String productionStage) {
    requireTeamExists(teamId);
    return repository.saveRepository(
        new Repository(key, organization, project, blankToNull(teamId), productionStage));
  }

  @Override
  public Repository mapToTeam(String repositoryKey, String teamId) {
    Repository repo =
        repository
            .findRepository(repositoryKey)
            .orElseThrow(
                () -> new NoSuchElementException("repository not found: " + repositoryKey));
    requireTeamExists(teamId);
    return repository.saveRepository(repo.assignTo(blankToNull(teamId)));
  }

  @Override
  public void delete(String repositoryKey) {
    repository.deleteRepository(repositoryKey);
  }

  private void requireTeamExists(String teamId) {
    if (teamId != null && !teamId.isBlank()) {
      repository
          .findTeam(teamId)
          .orElseThrow(() -> new IllegalArgumentException("team not found: " + teamId));
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
