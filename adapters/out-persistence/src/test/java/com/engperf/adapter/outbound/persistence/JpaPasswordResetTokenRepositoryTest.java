package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.PasswordResetToken;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration test against a real PostgreSQL provisioned by Testcontainers. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPasswordResetTokenRepository.class, JpaUserAccountRepository.class})
@Testcontainers
class JpaPasswordResetTokenRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JpaPasswordResetTokenRepository repo;
  @Autowired private JpaUserAccountRepository accounts;

  @Test
  void roundTripsIssuanceConsumptionAndWindowCount() {
    accounts.save(
        new UserAccount(
            "u:ana", "Ana", "ana@x.com", Role.MANAGER, AccountStatus.ACTIVE, null, "h:secret"));

    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    repo.save(new PasswordResetToken("prt:1", "u:ana", "hash-1", t0, t0.plusSeconds(3600), null));
    repo.save(
        new PasswordResetToken(
            "prt:2", "u:ana", "hash-2", t0.plusSeconds(10), t0.plusSeconds(3610), null));

    assertThat(repo.findByTokenHash("hash-1")).isPresent();
    assertThat(repo.findByTokenHash("unknown")).isEmpty();
    assertThat(repo.findPendingByAccount("u:ana")).hasSize(2);
    assertThat(repo.countIssuedSince("u:ana", t0)).isEqualTo(2);
    assertThat(repo.countIssuedSince("u:ana", t0.plusSeconds(20))).isZero();

    repo.consumeAllPending("u:ana", t0.plusSeconds(30));

    assertThat(repo.findPendingByAccount("u:ana")).isEmpty();
    assertThat(repo.findByTokenHash("hash-1"))
        .get()
        .extracting(PasswordResetToken::isConsumed)
        .isEqualTo(true);
  }
}
