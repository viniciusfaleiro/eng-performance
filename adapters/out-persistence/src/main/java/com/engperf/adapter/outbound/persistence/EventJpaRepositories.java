package com.engperf.adapter.outbound.persistence;

import com.engperf.domain.metrics.EventType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data repository for raw events, queried by type over a half-open time window. */
interface RawEventJpaRepository extends JpaRepository<RawEventEntity, String> {

  List<RawEventEntity> findByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
      EventType type, Instant fromInclusive, Instant toExclusive);

  List<RawEventEntity> findByType(EventType type);

  /**
   * The distinct parents referenced by any work item. Native because the relationship lives inside
   * the {@code jsonb} detail; one column, no join, and the result grows with the number of items
   * ingested rather than with the window being read.
   */
  @Query(
      value =
          "SELECT DISTINCT detail->>'parent_event_id' FROM raw_event"
              + " WHERE detail->>'parent_event_id' IS NOT NULL",
      nativeQuery = true)
  List<String> findDistinctParentEventIds();
}
