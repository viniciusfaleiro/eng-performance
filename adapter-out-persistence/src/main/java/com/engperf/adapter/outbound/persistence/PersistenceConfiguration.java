package com.engperf.adapter.outbound.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Keeps JPA wiring inside the persistence adapter: entities and Spring Data repositories are
 * scanned from this package, so the bootstrap composition root needs no JPA knowledge.
 */
@Configuration
@EnableJpaRepositories
@EntityScan
class PersistenceConfiguration {}
