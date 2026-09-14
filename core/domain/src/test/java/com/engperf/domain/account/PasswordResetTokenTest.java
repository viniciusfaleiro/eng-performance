package com.engperf.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant EXPIRES = Instant.parse("2026-01-01T01:00:00Z");

  private static PasswordResetToken token() {
    return new PasswordResetToken("t:1", "u:ana", "hash", CREATED, EXPIRES, null);
  }

  @Test
  void rejectsExpiresAtNotAfterCreatedAt() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PasswordResetToken("t:1", "u:ana", "hash", CREATED, CREATED, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PasswordResetToken("t:1", "u:ana", "hash", EXPIRES, CREATED, null));
  }

  @Test
  void isUsableWhileUnexpiredAndUnconsumed() {
    PasswordResetToken t = token();
    assertThat(t.isConsumed()).isFalse();
    assertThat(t.isExpired(CREATED.plusSeconds(30 * 60))).isFalse();
    assertThat(t.isUsableAt(CREATED.plusSeconds(30 * 60))).isTrue();
  }

  @Test
  void isExpiredAtOrAfterExpiresAt() {
    PasswordResetToken t = token();
    assertThat(t.isExpired(EXPIRES)).isTrue();
    assertThat(t.isExpired(EXPIRES.plusSeconds(1))).isTrue();
    assertThat(t.isUsableAt(EXPIRES)).isFalse();
  }

  @Test
  void consumedTokenIsNeverUsableAgain() {
    PasswordResetToken consumed = token().consumedAt(CREATED.plusSeconds(60));
    assertThat(consumed.isConsumed()).isTrue();
    assertThat(consumed.consumedAt()).isEqualTo(CREATED.plusSeconds(60));
    assertThat(consumed.isUsableAt(CREATED.plusSeconds(61))).isFalse();
  }
}
