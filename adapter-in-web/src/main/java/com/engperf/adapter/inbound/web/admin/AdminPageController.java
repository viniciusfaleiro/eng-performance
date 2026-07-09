package com.engperf.adapter.inbound.web.admin;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.application.port.inbound.StructureUseCase;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Server-rendered Admin page (Estrutura / Identidades / Repositórios), styled after the prototype.
 */
@Controller
public class AdminPageController {

  private final StructureUseCase structure;
  private final RepositoryUseCase repositories;
  private final IdentityUseCase identities;

  public AdminPageController(
      StructureUseCase structure, RepositoryUseCase repositories, IdentityUseCase identities) {
    this.structure = structure;
    this.repositories = repositories;
    this.identities = identities;
  }

  @GetMapping("/admin")
  public String admin(Model model) {
    model.addAttribute("rows", structureRows());
    model.addAttribute("identities", identities.identities());
    model.addAttribute("coverage", identities.coverage());
    model.addAttribute("repos", repositories.repositories());
    return "admin";
  }

  private List<StructureRow> structureRows() {
    List<Person> people = structure.people();
    List<Team> teams = structure.teams();
    Map<String, String> nameById =
        people.stream().collect(Collectors.toMap(Person::id, Person::name, (a, b) -> a));
    List<StructureRow> rows = new ArrayList<>();
    for (Vertical vertical : structure.verticals()) {
      String verticalManager = displayManager(vertical.managerId(), nameById);
      List<Team> verticalTeams =
          teams.stream().filter(t -> t.verticalId().equals(vertical.id())).toList();
      if (verticalTeams.isEmpty()) {
        rows.add(new StructureRow(vertical.name(), verticalManager, "—", "—", "—"));
        continue;
      }
      for (Team team : verticalTeams) {
        String members =
            people.stream()
                .filter(p -> p.currentTeamId().filter(id -> id.equals(team.id())).isPresent())
                .map(Person::name)
                .collect(Collectors.joining(", "));
        rows.add(
            new StructureRow(
                vertical.name(),
                verticalManager,
                team.name(),
                displayManager(team.managerId(), nameById),
                members.isEmpty() ? "—" : members));
      }
    }
    return rows;
  }

  private static String displayManager(String managerId, Map<String, String> nameById) {
    if (managerId == null) {
      return "—";
    }
    return nameById.getOrDefault(managerId, managerId);
  }

  /** View row for the structure table. */
  public record StructureRow(
      String vertical, String verticalManager, String team, String teamManager, String people) {}
}
