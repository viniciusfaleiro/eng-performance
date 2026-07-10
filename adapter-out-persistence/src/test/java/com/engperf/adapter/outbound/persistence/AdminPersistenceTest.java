package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration test for the accounts and config adapters against a real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUserAccountRepository.class, JpaPlatformConfigRepository.class})
@Testcontainers
class AdminPersistenceTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JpaUserAccountRepository accounts;
  @Autowired private JpaPlatformConfigRepository config;

  @Test
  void persistsAndFindsAccounts() {
    accounts.save(
        new UserAccount(
            "u:ana", "Ana", "ana@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "hash"));

    assertThat(accounts.findByEmail("ana@x.com"))
        .get()
        .extracting(UserAccount::role)
        .isEqualTo(Role.MANAGER);
    assertThat(accounts.findById("u:ana")).isPresent();
    accounts.deleteById("u:ana");
    assertThat(accounts.findAll()).isEmpty();
  }

  @Test
  void persistsSingletonConfig() {
    config.saveAdoIntegration(
        new AdoIntegration("https://dev.azure.com/org", "pat", "prod", true, null));
    config.saveAiConvention(new AiConvention(AiStrategy.TAG, null, "[ai]", null, true));

    assertThat(config.getAdoIntegration())
        .extracting(AdoIntegration::organizationUrl)
        .isEqualTo("https://dev.azure.com/org");
    assertThat(config.getAiConvention()).extracting(AiConvention::tag).isEqualTo("[ai]");
  }
}
