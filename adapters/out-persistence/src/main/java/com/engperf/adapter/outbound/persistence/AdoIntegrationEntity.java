package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA mapping for the singleton Azure DevOps integration marker (id = "default"). */
@Entity
@Table(name = "ado_integration")
public class AdoIntegrationEntity {

  @Id private String id;

  @Column(nullable = false)
  private boolean connected;

  @Column(name = "last_validated_at")
  private Instant lastValidatedAt;

  protected AdoIntegrationEntity() {}

  public AdoIntegrationEntity(String id, boolean connected, Instant lastValidatedAt) {
    this.id = id;
    this.connected = connected;
    this.lastValidatedAt = lastValidatedAt;
  }

  public boolean isConnected() {
    return connected;
  }

  public Instant getLastValidatedAt() {
    return lastValidatedAt;
  }
}
