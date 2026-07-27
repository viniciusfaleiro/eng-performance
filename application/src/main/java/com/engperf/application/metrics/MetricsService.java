package com.engperf.application.metrics;

import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.RawEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Reads the structure + events through the ports, builds a {@link StructureIndex}, and delegates to
 * the {@link MetricsEngine}. The reference "today" comes from an injected {@link Clock} so the seed
 * window and screenshots are deterministic.
 */
public final class MetricsService implements MetricsQueryUseCase {

  private static final int BUCKETS = 12;

  private final StructureRepositoryPort structure;
  private final EventStorePort events;
  private final MetricCatalog catalog;
  private final Clock clock;

  public MetricsService(
      StructureRepositoryPort structure,
      EventStorePort events,
      MetricCatalog catalog,
      Clock clock) {
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public List<MetricDefinition> catalog() {
    return catalog.all();
  }

  @Override
  public List<MetricCard> cards(String nodeId, Frequency frequency) {
    LocalDate reference = LocalDate.now(clock);
    StructureIndex index = buildIndex();
    List<MetricCard> cards = new ArrayList<>();
    for (MetricDefinition def : catalog.all()) {
      List<RawEvent> window = fetch(def, frequency, reference);
      cards.add(MetricsEngine.card(index, window, def, nodeId, frequency, reference, BUCKETS));
    }
    return cards;
  }

  @Override
  public MetricSeries series(String metricKey, String nodeId, Frequency frequency) {
    MetricDefinition def = definition(metricKey);
    LocalDate reference = LocalDate.now(clock);
    List<RawEvent> window = fetch(def, frequency, reference);
    return MetricsEngine.series(buildIndex(), window, def, nodeId, frequency, reference, BUCKETS);
  }

  @Override
  public MetricSeries cohortSeries(
      String metricKey, String nodeId, Frequency frequency, boolean aiAssisted) {
    MetricDefinition def = definition(metricKey);
    LocalDate reference = LocalDate.now(clock);
    List<RawEvent> window = fetch(def, frequency, reference);
    return MetricsEngine.series(
        buildIndex(),
        window,
        def,
        nodeId,
        frequency,
        reference,
        BUCKETS,
        e -> e.ai() == aiAssisted);
  }

  private MetricDefinition definition(String metricKey) {
    return catalog
        .find(metricKey)
        .orElseThrow(() -> new NoSuchElementException("unknown metric: " + metricKey));
  }

  private StructureIndex buildIndex() {
    return new StructureIndex(
        structure.findPeople(),
        structure.findTeams(),
        structure.findRepositories(),
        structure.findIdentities());
  }

  private List<RawEvent> fetch(MetricDefinition def, Frequency frequency, LocalDate reference) {
    List<Bucket> buckets = frequency.lastBuckets(reference, BUCKETS);
    Instant from = buckets.get(0).start().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to =
        buckets.get(buckets.size() - 1).endExclusive().atStartOfDay(ZoneOffset.UTC).toInstant();
    return events.findByTypeBetween(def.eventType(), from, to);
  }
}
