package com.engperf.domain.metrics;

import com.engperf.domain.common.Text;
import java.util.Objects;
import java.util.Optional;

/**
 * A catalog entry: what a metric is and how it is computed. The engine reads {@code scope} and
 * {@code aggregation} to compute values, {@code measure} to pick which per-event field it reads
 * (the event's numeric {@code value} or a named detail key), and {@code direction} to resolve the
 * change {@link Sentiment}. {@code eventType} selects which raw events feed it. {@code bands}, when
 * present, classify the value into a benchmark {@link Tier} (DORA metrics only).
 */
public record MetricDefinition(
    String key,
    String label,
    String group,
    EventType eventType,
    AttributionScope scope,
    Aggregation aggregation,
    String measure,
    String unit,
    Direction direction,
    TierBands bands) {

  /** The default measure: the event's own numeric value. */
  public static final String VALUE = "value";

  public MetricDefinition {
    key = Text.required(key, "metric key");
    label = Text.required(label, "metric label");
    group = Text.required(group, "metric group");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(scope, "scope must not be null");
    Objects.requireNonNull(aggregation, "aggregation must not be null");
    measure = measure == null || measure.isBlank() ? VALUE : measure.strip();
    unit = Text.optional(unit);
    Objects.requireNonNull(direction, "direction must not be null");
  }

  /** S3-style entry: default measure ({@code value}) and no benchmark tiers. */
  public MetricDefinition(
      String key,
      String label,
      String group,
      EventType eventType,
      AttributionScope scope,
      Aggregation aggregation,
      String unit,
      Direction direction) {
    this(key, label, group, eventType, scope, aggregation, VALUE, unit, direction, null);
  }

  public boolean readsDefaultMeasure() {
    return VALUE.equals(measure);
  }

  public Optional<TierBands> tierBands() {
    return Optional.ofNullable(bands);
  }
}
