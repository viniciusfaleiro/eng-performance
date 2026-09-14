package com.engperf.domain.account;

import com.engperf.domain.common.Text;
import java.time.Instant;
import java.util.Objects;

/**
 * A single-use password reset token. Only the hash is ever persisted — the raw token exists only in
 * the email sent to the user. Usable while unexpired and unconsumed.
 *
 * @param tokenHash SHA-256 hash of the raw token, hex-encoded
 * @param consumedAt when the token was consumed, or {@code null} while pending
 */
public record PasswordResetToken(
    String id,
    String accountId,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant consumedAt) {

  public PasswordResetToken {
    id = Text.required(id, "id");
    accountId = Text.required(accountId, "accountId");
    tokenHash = Text.required(tokenHash, "tokenHash");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public boolean isExpired(Instant at) {
    return !at.isBefore(expiresAt);
  }

  public boolean isUsableAt(Instant at) {
    return !isConsumed() && !isExpired(at);
  }

  /** Returns a copy of this token marked consumed at {@code at}. */
  public PasswordResetToken consumedAt(Instant at) {
    Objects.requireNonNull(at, "at must not be null");
    return new PasswordResetToken(id, accountId, tokenHash, createdAt, expiresAt, at);
  }
}
