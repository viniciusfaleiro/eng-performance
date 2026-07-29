package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.StructureDtos.AssignIdentityRequest;
import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.structure.Coverage;
import com.engperf.domain.structure.CommitterIdentity;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for committer identities and attribution coverage. */
@RestController
public class IdentityController {

  private final IdentityUseCase identities;

  public IdentityController(IdentityUseCase identities) {
    this.identities = identities;
  }

  @GetMapping("/api/admin/ado/committers")
  public List<CommitterIdentity> committers() {
    return identities.identities();
  }

  @PostMapping("/api/admin/ado/committers")
  public CommitterIdentity assign(@RequestBody AssignIdentityRequest request) {
    return identities.assign(request.identity(), request.personId());
  }

  /** Reload identities from the ingested events and auto-link them to people by e-mail. */
  @PostMapping("/api/admin/ado/committers/reload")
  public IdentityUseCase.Reload reload() {
    return identities.reload();
  }

  @GetMapping("/api/admin/coverage")
  public Coverage coverage() {
    return identities.coverage();
  }
}
