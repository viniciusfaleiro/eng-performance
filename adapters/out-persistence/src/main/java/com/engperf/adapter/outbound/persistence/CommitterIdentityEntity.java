package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for a discovered committer identity, optionally linked to a person. */
@Entity
@Table(name = "committer_identity")
public class CommitterIdentityEntity {

  @Id private String identity;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "person_id")
  private String personId;

  @Column(name = "commit_count", nullable = false)
  private long commitCount;

  protected CommitterIdentityEntity() {}

  public CommitterIdentityEntity(
      String identity, String displayName, String personId, long commitCount) {
    this.identity = identity;
    this.displayName = displayName;
    this.personId = personId;
    this.commitCount = commitCount;
  }

  public String getIdentity() {
    return identity;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPersonId() {
    return personId;
  }

  public long getCommitCount() {
    return commitCount;
  }
}
