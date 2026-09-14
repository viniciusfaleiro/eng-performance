package com.engperf.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repositories for accounts and the singleton configuration rows. */
interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, String> {
  Optional<UserAccountEntity> findByEmail(String email);
}

interface AdoIntegrationJpaRepository extends JpaRepository<AdoIntegrationEntity, String> {}

interface AiConventionJpaRepository extends JpaRepository<AiConventionEntity, String> {}

interface SmtpSettingsJpaRepository extends JpaRepository<SmtpSettingsEntity, String> {}

interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, String> {
  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  List<PasswordResetTokenEntity> findByAccountIdAndConsumedAtIsNull(String accountId);

  long countByAccountIdAndCreatedAtGreaterThanEqual(String accountId, Instant since);
}
