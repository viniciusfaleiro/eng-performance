package com.engperf.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repositories backing the structure cadastro (one per aggregate). */
interface VerticalJpaRepository extends JpaRepository<VerticalEntity, String> {}

interface TeamJpaRepository extends JpaRepository<TeamEntity, String> {}

interface PersonJpaRepository extends JpaRepository<PersonEntity, String> {}

interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, String> {}

interface CommitterIdentityJpaRepository extends JpaRepository<CommitterIdentityEntity, String> {}
