package com.engperf.application.port.outbound;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Outbound port for the raw event store. The seed fills it today; the Azure DevOps adapter (S9)
 * fills the same events later — the engine reads through this port either way.
 */
public interface EventStorePort {

  void saveAll(Collection<RawEvent> events);

  /** Events of a type whose {@code occurredAt} is in [fromInclusive, toExclusive). */
  List<RawEvent> findByTypeBetween(EventType type, Instant fromInclusive, Instant toExclusive);

  long count();
}
