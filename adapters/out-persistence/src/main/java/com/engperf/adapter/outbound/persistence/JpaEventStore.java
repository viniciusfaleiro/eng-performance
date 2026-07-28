package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed adapter for {@link EventStorePort}; {@code detail} is (de)serialized as JSON.
 */
@Component
@Transactional
public class JpaEventStore implements EventStorePort {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

  private final RawEventJpaRepository events;

  public JpaEventStore(RawEventJpaRepository events) {
    this.events = events;
  }

  @Override
  public void saveAll(Collection<RawEvent> batch) {
    events.saveAll(batch.stream().map(JpaEventStore::toEntity).toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<RawEvent> findByTypeBetween(
      EventType type, Instant fromInclusive, Instant toExclusive) {
    return events
        .findByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            type, fromInclusive, toExclusive)
        .stream()
        .map(JpaEventStore::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long count() {
    return events.count();
  }

  private static RawEventEntity toEntity(RawEvent e) {
    return new RawEventEntity(e, writeDetail(e.detail()));
  }

  private static RawEvent toDomain(RawEventEntity e) {
    return new RawEvent(
        e.getId(),
        e.getType(),
        e.getOccurredAt(),
        e.getRepoKey(),
        e.getCommitterIdentity(),
        e.getNumericValue(),
        e.getPhase(),
        e.isAi(),
        readDetail(e.getDetail()));
  }

  private static String writeDetail(Map<String, String> detail) {
    try {
      return JSON.writeValueAsString(detail == null ? Map.of() : detail);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return "{}";
    }
  }

  private static Map<String, String> readDetail(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return JSON.readValue(json, MAP_TYPE);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return Map.of();
    }
  }
}
