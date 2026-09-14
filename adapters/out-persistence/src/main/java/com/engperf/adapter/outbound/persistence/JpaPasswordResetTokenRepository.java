package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.PasswordResetTokenPort;
import com.engperf.domain.account.PasswordResetToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed adapter for {@link PasswordResetTokenPort}. */
@Component
@Transactional
public class JpaPasswordResetTokenRepository implements PasswordResetTokenPort {

  private final PasswordResetTokenJpaRepository tokens;

  public JpaPasswordResetTokenRepository(PasswordResetTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  @Override
  public PasswordResetToken save(PasswordResetToken token) {
    tokens.save(
        new PasswordResetTokenEntity(
            token.id(),
            token.accountId(),
            token.tokenHash(),
            token.createdAt(),
            token.expiresAt(),
            token.consumedAt()));
    return token;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
    return tokens.findByTokenHash(tokenHash).map(JpaPasswordResetTokenRepository::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PasswordResetToken> findPendingByAccount(String accountId) {
    return tokens.findByAccountIdAndConsumedAtIsNull(accountId).stream()
        .map(JpaPasswordResetTokenRepository::toDomain)
        .toList();
  }

  @Override
  public void consumeAllPending(String accountId, Instant at) {
    for (PasswordResetTokenEntity e : tokens.findByAccountIdAndConsumedAtIsNull(accountId)) {
      tokens.save(
          new PasswordResetTokenEntity(
              e.getId(),
              e.getAccountId(),
              e.getTokenHash(),
              e.getCreatedAt(),
              e.getExpiresAt(),
              at));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public long countIssuedSince(String accountId, Instant since) {
    return tokens.countByAccountIdAndCreatedAtGreaterThanEqual(accountId, since);
  }

  private static PasswordResetToken toDomain(PasswordResetTokenEntity e) {
    return new PasswordResetToken(
        e.getId(),
        e.getAccountId(),
        e.getTokenHash(),
        e.getCreatedAt(),
        e.getExpiresAt(),
        e.getConsumedAt());
  }
}
