package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA mapping for a single-use password reset token. Only the hash is ever stored. */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetTokenEntity {

  @Id private String id;

  @Column(name = "account_id", nullable = false)
  private String accountId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  protected PasswordResetTokenEntity() {}

  public PasswordResetTokenEntity(
      String id,
      String accountId,
      String tokenHash,
      Instant createdAt,
      Instant expiresAt,
      Instant consumedAt) {
    this.id = id;
    this.accountId = accountId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
  }

  public String getId() {
    return id;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }
}
