package com.engperf.application.metrics;

import com.engperf.application.metrics.StructureIndex.Attribution;
import com.engperf.domain.metrics.Bucket;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.InProgressSpans;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * On-read aggregation. Collects every event that attributes (as-of-event) to the queried node and
 * aggregates the population directly — median/ratio are never composed from children. Handles
 * bucketing, correct-polarity evolution, the partial current period, and coverage. A population
 * predicate can restrict the metric to a cohort of events selected by an attribute (e.g. the AI
 * flag), so the same metric can be evaluated over disjoint cohorts of the same node.
 */
public final class MetricsEngine {

  private MetricsEngine() {}

  private static final InProgressSpans EMPTY_SPANS = new InProgressSpans(List.of());

  /**
   * One event that attributed to the queried node.
   *
   * <p>{@code spans} carries the event's in-progress intervals, read once here rather than per
   * bucket: the trend chart asks about twelve buckets and the same parse would otherwise run twelve
   * times per item. It is empty for every metric read at an instant, which is all of them but one.
   */
  record Matched(
      LocalDate date,
      Instant at,
      double measure,
      boolean hasMeasure,
      double numerator,
      double denominator,
      String entity,
      RawEvent source,
      InProgressSpans spans) {}

  public static MetricSeries series(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      int bucketCount) {
    return series(index, events, def, nodeId, period, readNow, bucketCount, e -> true);
  }

  /** As {@link #series}, but only events matching {@code population} feed the metric. */
  public static MetricSeries series(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      int bucketCount,
      Predicate<RawEvent> population) {
    return series(
        index,
        events,
        def,
        nodeId,
        period,
        readNow,
        bucketCount,
        new MetricPopulation(population, WorkTypeFilter.none()));
  }

  /**
   * As {@link #series}, restricted to a cohort and to a work-type selection — the two compose, so
   * the AI cohort of bug work is one population and not two readings combined afterwards.
   */
  public static MetricSeries series(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      int bucketCount,
      MetricPopulation requested) {

    Predicate<RawEvent> population = requested.cohort();
    WorkTypeFilter workTypes = requested.workTypes();
    boolean filtered = filters(def, workTypes);
    Predicate<RawEvent> selected = restrict(population, def, workTypes);
    Frequency freq = period.frequency();
    // Um relógio só. O motor precisava de uma data (o período em curso) e passou a precisar de um
    // instante (até quando um item aberto está em progresso); receber os dois convidaria os dois a
    // divergirem.
    LocalDate today = LocalDate.ofInstant(readNow, ZoneOffset.UTC);
    List<Matched> matched = match(index, events, def, nodeId, selected, readNow);
    List<Bucket> buckets = period.slices(bucketCount);

    double[] values = new double[buckets.size()];
    for (int i = 0; i < buckets.size(); i++) {
      values[i] = MetricsAggregation.of(def, inBucket(matched, buckets.get(i), def));
    }

    int last = buckets.size() - 1;
    Bucket current = buckets.get(last);
    int fullDays = (int) (current.endExclusive().toEpochDay() - current.start().toEpochDay());
    // A fatia decorrida só existe para o período que ainda está correndo. Um período passado já
    // terminou: comparar "1 dia de julho" contra "1 dia de junho" seria inventar um recorte.
    int elapsed = period.inProgress(today) ? freq.elapsedDays(current.start(), today) : fullDays;
    boolean partial = elapsed < fullDays;

    List<SeriesPoint> points = new ArrayList<>();
    for (int i = 0; i < buckets.size(); i++) {
      Double previous;
      if (i == 0) {
        previous = null;
      } else if (i == last && partial) {
        // Compare the elapsed slice against the same elapsed slice of the previous bucket.
        LocalDate prevStart = buckets.get(i - 1).start();
        Bucket sameSlice = new Bucket(prevStart, prevStart.plusDays(elapsed));
        previous = MetricsAggregation.of(def, inBucket(matched, sameSlice, def));
      } else {
        previous = values[i - 1];
      }
      points.add(
          new SeriesPoint(
              buckets.get(i).start().toString(),
              MetricValue.of(values[i], previous, def.direction())));
    }

    return new MetricSeries(
        def,
        points,
        coverage(index, events, def, selected),
        filtered,
        untypedExcluded(index, events, def, nodeId, population, filtered));
  }

  public static MetricCard card(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      int bucketCount) {
    return card(index, events, def, nodeId, period, readNow, bucketCount, e -> true);
  }

  /** As {@link #card}, but only events matching {@code population} feed the metric. */
  public static MetricCard card(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      int bucketCount,
      Predicate<RawEvent> population) {
    return card(
        index,
        events,
        def,
        nodeId,
        period,
        readNow,
        bucketCount,
        new MetricPopulation(population, WorkTypeFilter.none()));
  }

  /** As {@link #card}, restricted to a cohort and to a work-type selection. */
  public static MetricCard card(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Period period,
      Instant readNow,
      int bucketCount,
      MetricPopulation requested) {
    Predicate<RawEvent> population = requested.cohort();
    WorkTypeFilter workTypes = requested.workTypes();
    boolean filtered = filters(def, workTypes);
    Predicate<RawEvent> selected = restrict(population, def, workTypes);
    List<Matched> matched = match(index, events, def, nodeId, selected, readNow);
    return new MetricCard(
        def,
        selectedValue(matched, def, period, LocalDate.ofInstant(readNow, ZoneOffset.UTC)),
        coverage(index, events, def, selected),
        filtered,
        untypedExcluded(index, events, def, nodeId, population, filtered));
  }

  /**
   * Whether this selection reaches this metric. A deploy metric never filters (see {@link
   * MetricDefinition#filterableByWorkType()}), and an empty selection filters nothing at all.
   */
  public static boolean filters(MetricDefinition def, WorkTypeFilter workTypes) {
    return !workTypes.isEmpty() && def.filterableByWorkType();
  }

  /**
   * The cohort predicate narrowed by the work-type selection, when the selection applies. The two
   * are composed into one population so every statistic — median included — is recomputed over the
   * selected work instead of being assembled from per-type values.
   */
  public static Predicate<RawEvent> restrict(
      Predicate<RawEvent> population, MetricDefinition def, WorkTypeFilter workTypes) {
    return filters(def, workTypes) ? population.and(workTypes::matches) : population;
  }

  /**
   * How many of the node's events the selection dropped for carrying no work type — a pull request
   * linked to no work item, typically. Counted apart from coverage on purpose: coverage answers
   * attribution, and folding these in would make two different problems read as one.
   */
  private static long untypedExcluded(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Predicate<RawEvent> population,
      boolean filtered) {
    if (!filtered) {
      return 0L;
    }
    long n = 0;
    for (RawEvent e : events) {
      if (!population.test(e) || !WorkTypeFilter.isUntyped(e)) {
        continue;
      }
      Optional<Attribution> attr = index.attribute(e, def.scope());
      if (attr.isPresent() && attr.get().belongsTo(nodeId)) {
        n++;
      }
    }
    return n;
  }

  /**
   * The value for the period actually selected, compared against the interval before it.
   *
   * <p>For a calendar bucket this is the same number the last point of the series carries, by the
   * same formula. For a chosen interval it has to be computed here instead: the last point of the
   * series is the interval's last <em>slice</em> — the final partial month of a range, say — and a
   * card showing that would answer a question nobody asked.
   */
  private static MetricValue selectedValue(
      List<Matched> matched, MetricDefinition def, Period period, LocalDate today) {
    double now =
        MetricsAggregation.of(
            def, inBucket(matched, new Bucket(period.start(), period.end()), def));
    Period before = period.previous();
    // A fatia decorrida só existe para o balde que ainda está correndo. Um período passado já
    // terminou, e um intervalo escolhido é por definição o que foi pedido: comparar "1 dia de
    // julho" contra "1 dia de junho" seria inventar um recorte em qualquer um dos dois casos.
    Bucket baseline =
        period.inProgress(today)
            ? new Bucket(
                before.start(),
                before.start().plusDays(period.frequency().elapsedDays(period.start(), today)))
            : new Bucket(before.start(), before.end());
    return MetricValue.of(
        now, MetricsAggregation.of(def, inBucket(matched, baseline, def)), def.direction());
  }

  public static Coverage coverage(
      StructureIndex index, List<RawEvent> events, MetricDefinition def) {
    return coverage(index, events, def, e -> true);
  }

  private static Coverage coverage(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      Predicate<RawEvent> population) {
    long total = 0;
    long attributed = 0;
    for (RawEvent e : events) {
      if (!population.test(e)) {
        continue;
      }
      total++;
      if (index.attribute(e, def.scope()).isPresent()) {
        attributed++;
      }
    }
    return new Coverage(attributed, total);
  }

  static List<Matched> match(
      StructureIndex index,
      List<RawEvent> events,
      MetricDefinition def,
      String nodeId,
      Predicate<RawEvent> population,
      Instant readNow) {
    List<Matched> matched = new ArrayList<>();
    for (RawEvent e : events) {
      if (!population.test(e)) {
        continue;
      }
      Optional<Attribution> attr = index.attribute(e, def.scope());
      if (attr.isPresent() && attr.get().belongsTo(nodeId)) {
        Measure measure = measure(e, def.measure());
        matched.add(
            new Matched(
                e.occurredOn(),
                e.occurredAt(),
                measure.value(),
                measure.present(),
                detail(e, "num", e.ai() ? 1.0 : 0.0),
                detail(e, "den", 1.0),
                attr.get().entityKey(),
                e,
                def.readsIntervals() ? InProgressSpans.of(e, readNow) : EMPTY_SPANS));
      }
    }
    return matched;
  }

  private record Measure(double value, boolean present) {}

  /**
   * Resolves the per-event measure a metric reads: the event's numeric value ({@code "value"}) or a
   * named detail key. Absent measures are flagged so median/snapshot can exclude them.
   */
  private static Measure measure(RawEvent e, String measureKey) {
    if (MetricDefinition.VALUE.equals(measureKey)) {
      return new Measure(e.value(), e.numericValue() != null);
    }
    String raw = e.detail().get(measureKey);
    if (raw == null) {
      return new Measure(0.0, false);
    }
    try {
      return new Measure(Double.parseDouble(raw), true);
    } catch (NumberFormatException ex) {
      return new Measure(0.0, false);
    }
  }

  /** Reads a numeric field from the event detail, falling back to {@code fallback} when absent. */
  private static double detail(RawEvent e, String key, double fallback) {
    String v = e.detail().get(key);
    if (v == null) {
      return fallback;
    }
    try {
      return Double.parseDouble(v);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  /**
   * The matched events a period contains — the <strong>only</strong> place that decides it.
   *
   * <p>The card's value, every point of the trend chart and the openable item list all come through
   * here, which is why a change of concept has one place to happen and why the list cannot disagree
   * with the number above it.
   *
   * <p>An instant metric is contained by the period holding the event's own date. An interval
   * metric is contained by every period its in-progress interval overlaps, and the event's date
   * decides nothing — an item left in progress and untouched carries an old date and is still in
   * progress today.
   */
  static List<Matched> inBucket(List<Matched> matched, Bucket bucket, MetricDefinition def) {
    List<Matched> out = new ArrayList<>();
    boolean byInterval = def.readsIntervals();
    Instant from = byInterval ? startOf(bucket.start()) : null;
    Instant to = byInterval ? startOf(bucket.endExclusive()) : null;
    for (Matched m : matched) {
      boolean in = byInterval ? m.spans().overlaps(from, to) : bucket.contains(m.date());
      if (in) {
        out.add(m);
      }
    }
    return out;
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}
