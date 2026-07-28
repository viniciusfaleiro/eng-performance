package com.engperf.application.port.inbound;

import com.engperf.domain.structure.Repository;
import java.util.List;

/**
 * Inbound port: register repositories one by one (each with its organization and production stage),
 * map each to at most one team (1 repo → 1 team), and remove them.
 */
public interface RepositoryUseCase {

  List<Repository> repositories();

  /** Registers (or replaces) a repository and its org/project/team/production stage. */
  Repository register(
      String key, String organization, String project, String teamId, String productionStage);

  Repository mapToTeam(String repositoryKey, String teamId);

  void delete(String repositoryKey);
}
