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
  private String project;

  @Column(name = "team_id")
  private String teamId;

  protected RepositoryEntity() {}

  public RepositoryEntity(String repoKey, String project, String teamId) {
    this.repoKey = repoKey;
    this.project = project;
    this.teamId = teamId;
  }

  public String getRepoKey() {
    return repoKey;
  }

  public String getProject() {
    return project;
  }

  public String getTeamId() {
    return teamId;
  }
}
