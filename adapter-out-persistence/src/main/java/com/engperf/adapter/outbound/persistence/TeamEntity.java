package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for a team. */
@Entity
@Table(name = "team")
public class TeamEntity {

  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Column(name = "vertical_id", nullable = false)
  private String verticalId;

  @Column(name = "manager_id")
  private String managerId;

  @Column(name = "production_stage_override")
  private String productionStageOverride;

  protected TeamEntity() {}

  public TeamEntity(
      String id, String name, String verticalId, String managerId, String productionStageOverride) {
    this.id = id;
    this.name = name;
    this.verticalId = verticalId;
    this.managerId = managerId;
    this.productionStageOverride = productionStageOverride;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getVerticalId() {
    return verticalId;
  }

  public String getManagerId() {
    return managerId;
  }

  public String getProductionStageOverride() {
    return productionStageOverride;
  }
}
