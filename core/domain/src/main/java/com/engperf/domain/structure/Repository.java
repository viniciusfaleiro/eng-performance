package com.engperf.domain.structure;

/**
 * An Azure DevOps repository mapped to at most one {@link Team} (rule: 1 repo → 1 team). It is
 * self-describing across organizations: {@code organization} is its ADO org and {@code
 * productionStage} is the rule (stage/environment name) that marks a pipeline run as a production
 * deploy for this repo. Registered one by one by an admin — there is no org-wide discovery.
 *
 * @param teamId owning team, or {@code null} when unmapped (out of DORA scope)
 */
public record Repository(
    String key, String organization, String project, String teamId, String productionStage) {

  public Repository {
    key = Validation.text(key, "repository key");
    organization = Validation.text(organization, "organization");
    project = Validation.text(project, "project");
    teamId = Validation.optional(teamId);
    productionStage = Validation.optional(productionStage);
  }

  public boolean isMapped() {
    return teamId != null;
  }

  public Repository assignTo(String newTeamId) {
    return new Repository(key, organization, project, newTeamId, productionStage);
  }
}
