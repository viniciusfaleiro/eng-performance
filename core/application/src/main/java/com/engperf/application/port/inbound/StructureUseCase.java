package com.engperf.application.port.inbound;

import com.engperf.application.structure.TreeNode;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import java.util.List;

/** Inbound port: manage and navigate the organization structure. */
public interface StructureUseCase {

  TreeNode tree();

  List<Vertical> verticals();

  List<Team> teams();

  List<Person> people();

  Vertical createVertical(String name, String managerId);

  Vertical setVerticalManager(String verticalId, String managerId);

  Team createTeam(String name, String verticalId, String managerId);

  Team setTeamManager(String teamId, String managerId);

  Person createPerson(String name, String email, String teamId, LocalDate effectiveDate);

  Person movePerson(String personId, String teamId, LocalDate effectiveDate);
}
