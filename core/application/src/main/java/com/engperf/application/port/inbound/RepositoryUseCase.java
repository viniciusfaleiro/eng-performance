package com.engperf.application.port.inbound;

import com.engperf.domain.structure.Repository;
import java.util.List;

/** Inbound port: list repositories and map each to at most one team (1 repo → 1 team). */
public interface RepositoryUseCase {

  List<Repository> repositories();

  Repository mapToTeam(String repositoryKey, String teamId);
}
