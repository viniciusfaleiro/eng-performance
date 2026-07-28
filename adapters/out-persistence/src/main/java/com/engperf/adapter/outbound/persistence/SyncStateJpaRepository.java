package com.engperf.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SyncStateJpaRepository extends JpaRepository<SyncStateEntity, String> {}
