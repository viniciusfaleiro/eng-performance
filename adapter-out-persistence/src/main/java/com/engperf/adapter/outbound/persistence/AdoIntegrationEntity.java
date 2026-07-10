package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA mapping for the singleton Azure DevOps connection configuration (id = "default"). */
@Entity
@Table(name = "ado_integration")
public class AdoIntegrationEntity {

  @Id private String id;

  @Column(name = "organization_url")
  private String organizationUrl;

  @Column(name = "pat_secret")
  private String patSecret;

  @Column(name = "production_stage_rule")
  private String productionStageRule;

  @Column(nullable = false)
  private boolean connected;

  @Column(name = "last_validated_at")
  private Instant lastValidatedAt;

  protected AdoIntegrationEntity() {}

  public AdoIntegrationEntity(
      String id,
      String organizationUrl,
      String patSecret,
      String productionStageRule,
      boolean connected,
      Instant lastValidatedAt) {
    this.id = id;
    this.organizationUrl = organizationUrl;
    this.patSecret = patSecret;
    this.productionStageRule = productionStageRule;
    this.connected = connected;
    this.lastValidatedAt = lastValidatedAt;
  }

  public String getOrganizationUrl() {
    return organizationUrl;
  }

  public String getPatSecret() {
    return patSecret;
  }

  public String getProductionStageRule() {
    return productionStageRule;
  }

  public boolean isConnected() {
    return connected;
  }

  public Instant getLastValidatedAt() {
    return lastValidatedAt;
  }
}
