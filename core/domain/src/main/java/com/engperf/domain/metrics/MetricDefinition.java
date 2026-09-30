package com.engperf.domain.metrics;

import com.engperf.domain.common.Text;
import java.util.Objects;
import java.util.Optional;

/**
 * A catalog entry: what a metric is and how it is computed. The engine reads {@code scope} and
 * {@code aggregation} to compute values, {@code measure} to pick which per-event field it reads
 * (the event's numeric {@code value} or a named detail key), and {@code direction} to resolve the
 * change {@link Sentiment}. {@code eventType} selects which raw events feed it. {@code bands}, when
 * present, classify the value into a benchmark {@link Tier} (DORA metrics only). {@code occupation}
 * says whether its events happen at a moment or occupy a stretch of time, which is what decides
 * period membership. {@code explanation} is what the UI shows when a reader asks how the number was
 * produced — it lives here, next to the fields that decide the calculation, so that changing one
 * puts the other in view.
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
    TierBands bands,
    MetricExplanation explanation,
    Occupation occupation) {

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
    occupation = occupation == null ? Occupation.INSTANT : occupation;
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
    this(
        key,
        label,
        group,
        eventType,
        scope,
        aggregation,
        VALUE,
        unit,
        direction,
        null,
        null,
        Occupation.INSTANT);
  }

  /**
   * The same definition, reading a named detail key instead of the event's own numeric value.
   *
   * <p>A one-argument wither rather than a longer constructor: the parameter limit exists because a
   * call with nine positional arguments is unreadable and two of them get swapped sooner or later.
   * {@code new MetricDefinition(...).reading("cycle_h")} says which field is the measure at the
   * point where it matters.
   */
  public MetricDefinition reading(String detailKey) {
    return new MetricDefinition(
        key,
        label,
        group,
        eventType,
        scope,
        aggregation,
        detailKey,
        unit,
        direction,
        bands,
        explanation,
        occupation);
  }

  /** The same definition, classified into benchmark tiers — the DORA metrics only. */
  public MetricDefinition graded(TierBands tiers) {
    return new MetricDefinition(
        key,
        label,
        group,
        eventType,
        scope,
        aggregation,
        measure,
        unit,
        direction,
        tiers,
        explanation,
        occupation);
  }

  /** The same definition, carrying the text that explains it to a reader. */
  public MetricDefinition withExplanation(MetricExplanation text) {
    return new MetricDefinition(
        key,
        label,
        group,
        eventType,
        scope,
        aggregation,
        measure,
        unit,
        direction,
        bands,
        text,
        occupation);
  }

  /**
   * The same definition, counted over the interval its events occupy instead of at their dates.
   *
   * <p>Declared this way, and not as another constructor overload, so the exception stays visible:
   * every metric in the catalog reads at an instant except the ones that say otherwise right where
   * they are defined.
   */
  public MetricDefinition over(Occupation how) {
    return new MetricDefinition(
        key,
        label,
        group,
        eventType,
        scope,
        aggregation,
        measure,
        unit,
        direction,
        bands,
        explanation,
        how);
  }

  /** Whether this metric's events occupy an interval rather than an instant. */
  public boolean readsIntervals() {
    return occupation == Occupation.INTERVAL;
  }

  /** Absent while a metric has not been explained yet — see the catalog's completeness test. */
  public Optional<MetricExplanation> explained() {
    return Optional.ofNullable(explanation);
  }

  public boolean readsDefaultMeasure() {
    return VALUE.equals(measure);
  }

  public Optional<TierBands> tierBands() {
    return Optional.ofNullable(bands);
  }
}
