package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.StructureDtos.CreatePersonRequest;
import com.engperf.adapter.inbound.web.admin.StructureDtos.CreateTeamRequest;
import com.engperf.adapter.inbound.web.admin.StructureDtos.CreateVerticalRequest;
import com.engperf.adapter.inbound.web.admin.StructureDtos.ManagerRequest;
import com.engperf.adapter.inbound.web.admin.StructureDtos.PersonResponse;
import com.engperf.adapter.inbound.web.admin.StructureDtos.TeamChangeRequest;
import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.auth.ScopeViews;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.port.inbound.StructureUseCase;
import com.engperf.application.structure.TreeNode;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for the organization structure and its admin CRUD. */
@RestController
public class StructureController {

  private final StructureUseCase structure;

  public StructureController(StructureUseCase structure) {
    this.structure = structure;
  }

  @GetMapping("/api/structure/tree")
  public TreeNode tree(@RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    return ScopeViews.prune(structure.tree(), user.scope());
  }

  /**
   * Returns a single structure node, enforcing coaching-only visibility: 403 when the node is
   * outside the caller's scope (a team they don't manage, or a peer's individual view). This is the
   * seam the S3+ dashboards hang their metrics off of.
   */
  @GetMapping("/api/structure/nodes/{nodeId}")
  public TreeNode node(
      @PathVariable String nodeId, @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    if (!user.scope().canView(nodeId)) {
      throw new ForbiddenException("node outside access scope: " + nodeId);
    }
    return find(structure.tree(), nodeId)
        .orElseThrow(() -> new java.util.NoSuchElementException("node not found: " + nodeId));
  }

  private static java.util.Optional<TreeNode> find(TreeNode node, String id) {
    if (node.id().equals(id)) {
      return java.util.Optional.of(node);
    }
    for (TreeNode child : node.children()) {
      java.util.Optional<TreeNode> hit = find(child, id);
      if (hit.isPresent()) {
        return hit;
      }
    }
    return java.util.Optional.empty();
  }

  @GetMapping("/api/admin/verticals")
  public List<Vertical> verticals() {
    return structure.verticals();
  }

  @PostMapping("/api/admin/verticals")
  @ResponseStatus(HttpStatus.CREATED)
  public Vertical createVertical(@RequestBody CreateVerticalRequest request) {
    return structure.createVertical(request.name(), request.managerId());
  }

  @PutMapping("/api/admin/verticals/{id}/manager")
  public Vertical setVerticalManager(@PathVariable String id, @RequestBody ManagerRequest request) {
    return structure.setVerticalManager(id, request.managerId());
  }

  @GetMapping("/api/admin/teams")
  public List<Team> teams() {
    return structure.teams();
  }

  @PostMapping("/api/admin/teams")
  @ResponseStatus(HttpStatus.CREATED)
  public Team createTeam(@RequestBody CreateTeamRequest request) {
    return structure.createTeam(request.name(), request.verticalId(), request.managerId());
  }

  @PutMapping("/api/admin/teams/{id}/manager")
  public Team setTeamManager(@PathVariable String id, @RequestBody ManagerRequest request) {
    return structure.setTeamManager(id, request.managerId());
  }

  @GetMapping("/api/admin/people")
  public List<PersonResponse> people() {
    return structure.people().stream().map(PersonResponse::from).toList();
  }

  @PostMapping("/api/admin/people")
  @ResponseStatus(HttpStatus.CREATED)
  public PersonResponse createPerson(@RequestBody CreatePersonRequest request) {
    LocalDate effective =
        request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
    return PersonResponse.from(
        structure.createPerson(request.name(), request.email(), request.teamId(), effective));
  }

  @PostMapping("/api/admin/people/{id}/team-change")
  public PersonResponse teamChange(
      @PathVariable String id, @RequestBody TeamChangeRequest request) {
    return PersonResponse.from(structure.movePerson(id, request.teamId(), request.effectiveDate()));
  }
}
