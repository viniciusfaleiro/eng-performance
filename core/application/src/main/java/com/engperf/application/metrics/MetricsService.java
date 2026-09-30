package com.engperf.application.metrics;

import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricExplanation;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.Person;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  public Map<String, MetricExplanation> viewExplanations() {
    return catalog.viewExplanations();
  }

  @Override
  public String attributionNote() {
    return catalog.attributionNote();
  }

  @Override
  public List<MetricCard> cards(String nodeId, Period period) {
    Instant readNow = clock.instant();
    StructureIndex index = buildIndex();
    List<MetricCard> cards = new ArrayList<>();
    for (MetricDefinition def : catalog.all()) {
      List<RawEvent> window = fetch(def, period);
      cards.add(
          MetricsEngine.card(
              index, window, def, nodeId, period, readNow, BUCKETS, catalog.population(def.key())));
    }
    return cards;
  }

  @Override
  public MetricSeries series(String metricKey, String nodeId, Period period) {
    MetricDefinition def = definition(metricKey);
    List<RawEvent> window = fetch(def, period);
    return MetricsEngine.series(
        buildIndex(),
        window,
        def,
        nodeId,
        period,
        clock.instant(),
        BUCKETS,
        catalog.population(metricKey));
  }

  @Override
  public MetricSeries cohortSeries(
      String metricKey, String nodeId, Period period, boolean aiAssisted) {
    MetricDefinition def = definition(metricKey);
    List<RawEvent> window = fetch(def, period);
    return MetricsEngine.series(
        buildIndex(),
        window,
        def,
        nodeId,
        period,
        clock.instant(),
        BUCKETS,
        catalog.population(metricKey).and(e -> e.ai() == aiAssisted));
  }

  @Override
  public List<MetricDrilldownItem> items(String metricKey, String nodeId, Period period) {
    MetricDefinition def = definition(metricKey);
    List<RawEvent> window = fetch(def, period);
    return MetricsDrilldown.items(
        buildIndex(), window, def, nodeId, period, clock.instant(), catalog.population(metricKey));
  }

  @Override
  public List<EntityShare> entityShares(String metricKey, String nodeId, Period period) {
    MetricDefinition def = definition(metricKey);
    if (def.aggregation() != Aggregation.DISTINCT_RATIO) {
      throw new IllegalArgumentException(
          "métrica " + metricKey + " não é contada por pessoa — use o detalhamento de itens");
    }
    return EntityBreakdown.of(
            buildIndex(),
            fetch(def, period),
            def,
            nodeId,
            period,
            clock.instant(),
            catalog.population(metricKey))
        .stream()
        .map(
            s ->
                s.withLabel(
                    structure.findPerson(s.entityId()).map(Person::name).orElse(s.entityId())))
        .toList();
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

  /**
   * The event window the period needs: everything the series will draw plus the interval it
   * compares against. For a bucket that is still BUCKETS buckets ending at it; for a chosen
   * interval it grows with the interval, which is why neither case needs a rule of its own here.
   */
  private List<RawEvent> fetch(MetricDefinition def, Period period) {
    // Métrica de intervalo não se busca por janela de datas: o período a que o item pertence é o
    // que o trabalho dele atravessa, e a data do registro não diz nada sobre isso. Ver o contrato
    // de findByType para por que alargar a janela não substitui isto.
    if (def.readsIntervals()) {
      return events.findByType(def.eventType());
    }
    Bucket span = period.readSpan(BUCKETS);
    Instant from = span.start().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = span.endExclusive().atStartOfDay(ZoneOffset.UTC).toInstant();
    return events.findByTypeBetween(def.eventType(), from, to);
  }
}
