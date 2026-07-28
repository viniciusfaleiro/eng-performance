package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.StructureDtos.MapRepositoryRequest;
import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.domain.structure.Repository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter mapping repositories to teams (1 repo → 1 team). */
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

  @PutMapping("/api/admin/repositories/{key}/team")
  public Repository mapToTeam(@PathVariable String key, @RequestBody MapRepositoryRequest request) {
    return repositories.mapToTeam(key, request.teamId());
  }
}
