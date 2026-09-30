package com.engperf.application.metrics;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** In-memory {@link EventStorePort} shared by the metrics service tests. */
final class FakeEvents implements EventStorePort {
  private final List<RawEvent> all = new ArrayList<>();

  void add(RawEvent e) {
    all.add(e);
  }

  void clear() {
    all.clear();
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

  // Derivado do corpus inteiro, como o adapter real: é justamente a diferença entre "do corpus" e
  // "do período" que os testes da regra de folha precisam poder observar.
  @Override
  public List<RawEvent> findByType(EventType type) {
    return findByTypeBetween(type, Instant.MIN, Instant.MAX);
  }

  @Override
  public Set<String> parentWorkItemIds() {
    return all.stream()
        .map(e -> e.detail().get("parent_event_id"))
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public long count() {
    return all.size();
  }
}
