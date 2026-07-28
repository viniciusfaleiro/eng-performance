package com.engperf.domain.structure;

/**
 * An Azure DevOps repository/project mapped to at most one {@link Team} (rule: 1 repo → 1 team).
 * The mapping is the basis for attributing repository/pipeline-scoped metrics (DORA) to a team.
 *
 * @param teamId owning team, or {@code null} when unmapped (out of DORA scope)
 */
public record Repository(String key, String project, String teamId) {

  public Repository {
    key = Validation.text(key, "repository key");
    project = Validation.text(project, "project");
    teamId = Validation.optional(teamId);
  }

  public boolean isMapped() {
    return teamId != null;
  }

  public Repository assignTo(String newTeamId) {
    return new Repository(key, project, newTeamId);
  }
}
