package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.ado.SyncState;
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

/** Integration test for the Azure DevOps sync cursor against a real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaSyncState.class)
@Testcontainers
class SyncStatePersistenceTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JpaSyncState syncState;

  @Test
  void savesReadsAndUpsertsTheSingleCursor() {
    assertThat(syncState.load()).isEmpty();

    syncState.save(
        new SyncState(
            Instant.parse("2026-06-20T10:00:00Z"), Instant.parse("2026-06-20T11:00:00Z"), 100));
    assertThat(syncState.load())
        .get()
        .extracting(SyncState::eventCount, SyncState::watermark)
        .containsExactly(100L, Instant.parse("2026-06-20T10:00:00Z"));

    // A second sync advances the same single row (upsert), not a new one.
    syncState.save(
        new SyncState(
            Instant.parse("2026-06-25T10:00:00Z"), Instant.parse("2026-06-25T11:00:00Z"), 140));
    assertThat(syncState.load()).get().extracting(SyncState::eventCount).isEqualTo(140L);
  }
}
