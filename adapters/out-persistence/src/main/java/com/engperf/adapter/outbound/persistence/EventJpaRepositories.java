package com.engperf.adapter.outbound.persistence;

import com.engperf.domain.metrics.EventType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for raw events, queried by type over a half-open time window. */
interface RawEventJpaRepository extends JpaRepository<RawEventEntity, String> {

  List<RawEventEntity> findByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
      EventType type, Instant fromInclusive, Instant toExclusive);
}
