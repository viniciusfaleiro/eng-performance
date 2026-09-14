package com.engperf.application.port.outbound;

import com.engperf.domain.account.PasswordResetToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbound port: persistence for password reset tokens. */
public interface PasswordResetTokenPort {

  PasswordResetToken save(PasswordResetToken token);

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  List<PasswordResetToken> findPendingByAccount(String accountId);

  /** Marks every still-pending token of the account as consumed at {@code at}. */
  void consumeAllPending(String accountId, Instant at);

  /** Counts tokens issued for the account at or after {@code since}. */
  long countIssuedSince(String accountId, Instant since);
}
