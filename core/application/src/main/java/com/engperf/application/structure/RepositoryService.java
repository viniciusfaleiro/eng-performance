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
  public Repository mapToTeam(String repositoryKey, String teamId) {
    Repository repo =
        repository
            .findRepository(repositoryKey)
            .orElseThrow(
                () -> new NoSuchElementException("repository not found: " + repositoryKey));
    if (teamId != null && !teamId.isBlank()) {
      repository
          .findTeam(teamId)
          .orElseThrow(() -> new IllegalArgumentException("team not found: " + teamId));
    }
    return repository.saveRepository(repo.assignTo(teamId));
  }
}
