package com.engperf.adapter.inbound.web.admin;

import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.TeamMembership;
import java.time.LocalDate;
import java.util.List;

/** Request/response DTOs for the structure/admin endpoints (kept out of the domain). */
final class StructureDtos {

  private StructureDtos() {}

  record CreateVerticalRequest(String name, String managerId) {}

  record CreateTeamRequest(String name, String verticalId, String managerId) {}

  record CreatePersonRequest(String name, String email, String teamId, LocalDate effectiveDate) {}

  record TeamChangeRequest(String teamId, LocalDate effectiveDate) {}

  record ManagerRequest(String managerId) {}

  record AssignIdentityRequest(String identity, String personId) {}

  record MapRepositoryRequest(String teamId) {}

  record CreateRepositoryRequest(
      String key, String organization, String project, String teamId, String productionStage) {}

  record PersonResponse(
      String id, String name, String email, String teamId, List<TeamMembership> memberships) {

    static PersonResponse from(Person person) {
      return new PersonResponse(
          person.id(),
          person.name(),
          person.email().orElse(null),
          person.currentTeamId().orElse(null),
          person.memberships());
    }
  }
}
