package com.engperf.application.metrics;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** In-memory {@link EventStorePort} shared by the metrics service tests. */
final class FakeEvents implements EventStorePort {
  private final List<RawEvent> all = new ArrayList<>();

  void add(RawEvent e) {
    all.add(e);
  }

  @Override
  public void saveAll(Collection<RawEvent> events) {
    all.addAll(events);
  }

  @Override
  public List<RawEvent> findByTypeBetween(EventType type, Instant from, Instant to) {
    return all.stream()
        .filter(e -> e.type() == type)
        .filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
        .toList();
  }

  @Override
  public long count() {
    return all.size();
  }
}
