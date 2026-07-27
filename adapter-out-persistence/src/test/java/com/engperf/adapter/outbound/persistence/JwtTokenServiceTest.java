package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.engperf.application.auth.AuthPrincipal;
import com.engperf.domain.account.Role;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private static final String SECRET = "dev-secret-please-change-min-32-bytes!!";

  private final JwtTokenService tokens = new JwtTokenService(SECRET, Duration.ofHours(12));

  @Test
  void issuesAndVerifiesRoundTrip() {
    AuthPrincipal principal = new AuthPrincipal("u:ana", "ana@x.com", Role.MANAGER, "p:ana");
    String token = tokens.issue(principal);

    Optional<AuthPrincipal> verified = tokens.verify(token);
    assertThat(verified).isPresent();
    assertThat(verified.get().accountId()).isEqualTo("u:ana");
    assertThat(verified.get().email()).isEqualTo("ana@x.com");
    assertThat(verified.get().role()).isEqualTo(Role.MANAGER);
    assertThat(verified.get().personId()).isEqualTo("p:ana");
  }

  @Test
  void verifyRejectsTamperedOrForeignToken() {
    AuthPrincipal principal = new AuthPrincipal("u:ana", "ana@x.com", Role.ADMIN, null);
    String token = tokens.issue(principal);

    assertThat(tokens.verify(token + "x")).isEmpty();
    assertThat(tokens.verify(null)).isEmpty();
    assertThat(tokens.verify("   ")).isEmpty();

    JwtTokenService other =
        new JwtTokenService("another-secret-also-at-least-32-bytes!!", Duration.ofHours(1));
    assertThat(other.verify(token)).isEmpty();
  }

  @Test
  void verifyRejectsExpiredToken() {
    JwtTokenService shortLived = new JwtTokenService(SECRET, Duration.ofSeconds(-1));
    String token =
        shortLived.issue(new AuthPrincipal("u:ana", "ana@x.com", Role.CONTRIBUTOR, null));
    assertThat(shortLived.verify(token)).isEmpty();
  }

  @Test
  void rejectsWeakSecret() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtTokenService("too-short", Duration.ofHours(1)));
  }
}
