package com.engperf.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration test for the event store against a real PostgreSQL (Flyway builds the schema). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaEventStore.class)
@Testcontainers
class JpaEventStoreTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JpaEventStore store;

  @Test
  void roundTripsEventsWithJsonDetailAndQueriesByTypeAndWindow() {
    RawEvent pr =
        new RawEvent(
            "e1",
            EventType.PR,
            Instant.parse("2026-06-10T10:00:00Z"),
            null,
            "id-ana",
            8.0,
            "review",
            true,
            Map.of("num", "1", "den", "1"));
    RawEvent deployIn =
        new RawEvent(
            "e2",
            EventType.DEPLOY,
            Instant.parse("2026-06-15T10:00:00Z"),
            "r:web",
            null,
            40.0,
            null,
            false,
            Map.of("num", "0", "den", "1"));
    RawEvent deployOut =
        new RawEvent(
            "e3",
            EventType.DEPLOY,
            Instant.parse("2026-01-01T10:00:00Z"),
            "r:web",
            null,
            12.0,
            null,
            false,
            Map.of());
    store.saveAll(List.of(pr, deployIn, deployOut));

    assertThat(store.count()).isEqualTo(3);

    // Window [2026-06-01, 2026-07-01): only e2 (a DEPLOY) is in range; e3 is out, e1 is a PR.
    List<RawEvent> deploys =
        store.findByTypeBetween(
            EventType.DEPLOY,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z"));
    assertThat(deploys).singleElement().extracting(RawEvent::id).isEqualTo("e2");

    List<RawEvent> prs =
        store.findByTypeBetween(
            EventType.PR,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z"));
    assertThat(prs)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.ai()).isTrue();
              assertThat(e.phase()).isEqualTo("review");
              assertThat(e.detail()).containsEntry("num", "1").containsEntry("den", "1");
            });
  }
}
