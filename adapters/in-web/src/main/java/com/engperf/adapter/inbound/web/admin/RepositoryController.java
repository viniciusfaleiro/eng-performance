package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.StructureDtos.CreateRepositoryRequest;
import com.engperf.adapter.inbound.web.admin.StructureDtos.MapRepositoryRequest;
import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.domain.structure.Repository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for repositories: register them one by one (org/project/key + team +
 * production stage), re-map to a team, and delete. 1 repo → 1 team.
 */
@RestController
public class RepositoryController {

  private final RepositoryUseCase repositories;

  public RepositoryController(RepositoryUseCase repositories) {
    this.repositories = repositories;
  }

  @GetMapping("/api/admin/repositories")
  public List<Repository> repositories() {
    return repositories.repositories();
  }

  @PostMapping("/api/admin/repositories")
  @ResponseStatus(HttpStatus.CREATED)
  public Repository register(@RequestBody CreateRepositoryRequest request) {
    return repositories.register(
        request.key(),
        request.organization(),
        request.project(),
        request.teamId(),
        request.productionStage());
  }

  @PutMapping("/api/admin/repositories/{key}/team")
  public Repository mapToTeam(@PathVariable String key, @RequestBody MapRepositoryRequest request) {
    return repositories.mapToTeam(key, request.teamId());
  }

  @DeleteMapping("/api/admin/repositories/{key}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String key) {
    repositories.delete(key);
  }
}
