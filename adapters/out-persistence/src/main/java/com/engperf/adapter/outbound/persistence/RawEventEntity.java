package com.engperf.adapter.outbound.persistence;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping for a raw activity event. {@code detail} is stored as jsonb (a JSON string). */
@Entity
@Table(name = "raw_event")
public class RawEventEntity {

  @Id private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventType type;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "repo_key")
  private String repoKey;

  @Column(name = "committer_identity")
  private String committerIdentity;

  @Column(name = "numeric_value")
  private Double numericValue;

  @Column private String phase;

  @Column(nullable = false)
  private boolean ai;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private String detail;

  protected RawEventEntity() {}

  /** Maps a domain event; {@code detailJson} is the JSON encoding of {@link RawEvent#detail()}. */
  public RawEventEntity(RawEvent source, String detailJson) {
    this.id = source.id();
    this.type = source.type();
    this.occurredAt = source.occurredAt();
    this.repoKey = source.repoKey();
    this.committerIdentity = source.committerIdentity();
    this.numericValue = source.numericValue();
    this.phase = source.phase();
    this.ai = source.ai();
    this.detail = detailJson;
  }

  public String getId() {
    return id;
  }

  public EventType getType() {
    return type;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getRepoKey() {
    return repoKey;
  }

  public String getCommitterIdentity() {
    return committerIdentity;
  }

  public Double getNumericValue() {
    return numericValue;
  }

  public String getPhase() {
    return phase;
  }

  public boolean isAi() {
    return ai;
  }

  public String getDetail() {
    return detail;
  }
}
