package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for a repository mapped (at most) to one team. */
@Entity
@Table(name = "repository")
public class RepositoryEntity {

  @Id
  @Column(name = "repo_key")
  private String repoKey;

  @Column(nullable = false)
  private String organization;

  @Column(nullable = false)
  private String project;

  @Column(name = "team_id")
  private String teamId;

  @Column(name = "production_stage")
  private String productionStage;

  protected RepositoryEntity() {}

  public RepositoryEntity(
      String repoKey, String organization, String project, String teamId, String productionStage) {
    this.repoKey = repoKey;
    this.organization = organization;
    this.project = project;
    this.teamId = teamId;
    this.productionStage = productionStage;
  }

  public String getRepoKey() {
    return repoKey;
  }

  public String getOrganization() {
    return organization;
  }

  public String getProject() {
    return project;
  }

  public String getTeamId() {
    return teamId;
  }

  public String getProductionStage() {
    return productionStage;
  }
}
