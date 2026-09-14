package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test against a real PostgreSQL: the SMTP password is stored encrypted, and a save
 * without a new password preserves the one already stored.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPlatformConfigRepository.class, SecretCipher.class})
@TestPropertySource(
    properties = "CONFIG_ENCRYPTION_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Testcontainers
class JpaPlatformConfigRepositorySmtpTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JpaPlatformConfigRepository repo;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TestEntityManager em;

  private static SmtpSettings settings(String password) {
    return new SmtpSettings(
        true,
        "smtp.empresa.com",
        587,
        MailTransport.STARTTLS,
        "no-reply",
        password,
        "no-reply@empresa.com",
        "Eng Performance",
        "https://empresa.com");
  }

  @Test
  void passwordIsStoredEncryptedAndReadBackInClearText() {
    repo.saveSmtpSettings(settings("s3cret"));
    em.flush();

    String stored =
        jdbc.queryForObject(
            "select password_ciphertext from smtp_settings where id = 'default'", String.class);
    assertThat(stored).isNotEqualTo("s3cret").isNotBlank();

    assertThat(repo.getSmtpSettings().password()).isEqualTo("s3cret");
  }

  @Test
  void savingWithoutAPasswordPreservesTheStoredOne() {
    repo.saveSmtpSettings(settings("s3cret"));
    repo.saveSmtpSettings(settings(null));

    assertThat(repo.getSmtpSettings().password()).isEqualTo("s3cret");
    assertThat(repo.getSmtpSettings().host()).isEqualTo("smtp.empresa.com");
  }
}
