package com.engperf.domain.metrics;

import com.engperf.domain.common.Text;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/**
 * A raw activity event. Common dimensions are typed columns; anything type-specific lives in {@code
 * detail}. Optional dimensions are null when not applicable (e.g. a DEPLOY has no committer
 * identity). {@code numericValue} is the metric's raw measure (hours, lines, or {@code 1} for a
 * counter); {@code phase} labels a cycle-time phase.
 */
public record RawEvent(
    String id,
    EventType type,
    Instant occurredAt,
    String repoKey,
    String committerIdentity,
    Double numericValue,
    String phase,
    boolean ai,
    Map<String, String> detail) {

  public RawEvent {
    id = Text.required(id, "event id");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    repoKey = Text.optional(repoKey);
    committerIdentity = Text.optional(committerIdentity);
    phase = Text.optional(phase);
    detail = detail == null ? Map.of() : Map.copyOf(detail);
  }

  /** The UTC calendar date the event occurred on — the basis for bucketing and as-of-event. */
  public LocalDate occurredOn() {
    return occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
  }

  public double value() {
    return numericValue == null ? 0.0 : numericValue;
  }
}
