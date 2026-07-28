package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA mapping for the single-row Azure DevOps sync cursor. */
@Entity
@Table(name = "sync_state")
public class SyncStateEntity {

  @Id private String id;

  @Column private Instant watermark;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "event_count", nullable = false)
  private long eventCount;

  protected SyncStateEntity() {}

  public SyncStateEntity(String id, Instant watermark, Instant lastSyncedAt, long eventCount) {
    this.id = id;
    this.watermark = watermark;
    this.lastSyncedAt = lastSyncedAt;
    this.eventCount = eventCount;
  }

  public Instant getWatermark() {
    return watermark;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public long getEventCount() {
    return eventCount;
  }
}
