package com.engperf.adapter.outbound.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repositories for accounts and the singleton configuration rows. */
interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, String> {
  Optional<UserAccountEntity> findByEmail(String email);
}

interface AdoIntegrationJpaRepository extends JpaRepository<AdoIntegrationEntity, String> {}

interface AiConventionJpaRepository extends JpaRepository<AiConventionEntity, String> {}
