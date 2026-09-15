package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricsEngine#items} must list exactly the events {@link MetricsEngine#aggregate} used —
 * one test per {@link Aggregation}, mirroring the selection rule for each.
 */
class MetricsEngineItemsTest {

  private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
  private static final LocalDate BUCKET_START = LocalDate.of(2026, 6, 1);

  private static StructureIndex index() {
    Person ana = Person.create("p:ana", "Ana", null, "t:eng", JAN1);
    Person bruno = Person.create("p:bruno", "Bruno", null, "t:eng", JAN1);
    return new StructureIndex(
        List.of(ana, bruno),
        List.of(new Team("t:eng", "Eng", "v:eng", "p:ana", null)),
        List.of(),
        List.of(
            new CommitterIdentity("id-ana", "Ana", "p:ana", 0),
            new CommitterIdentity("id-bruno", "Bruno", "p:bruno", 0)));
  }

  private static RawEvent event(String id, String identity, Map<String, String> detail) {
    return new RawEvent(
        id,
        EventType.WORKITEM,
        Instant.parse("2026-06-10T12:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        detail);
  }

  private static MetricDefinition def(String measure, Aggregation agg) {
    return new MetricDefinition(
        "k",
        "K",
        "fluxo",
        EventType.WORKITEM,
        AttributionScope.PERSON,
        agg,
        measure,
        "u",
        Direction.HIGHER_BETTER,
        null,
        null);
  }

  @Test
  void sumCountsEveryMatchedItem() {
    RawEvent e1 = event("wi:1", "id-ana", Map.of());
    RawEvent e2 = event("wi:2", "id-bruno", Map.of());
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(e1, e2),
            def(MetricDefinition.VALUE, Aggregation.SUM),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    assertThat(items).hasSize(2);
    assertThat(items).allMatch(MetricDrilldownItem::counted);
    // A SUM counts events, so each one contributes exactly 1 — not its (absent) raw measure.
    // Reporting the measure here would show "0" for events with no numeric value, like a commit.
    assertThat(items).extracting(MetricDrilldownItem::measure).containsExactly(1.0, 1.0);
  }

  @Test
  void medianExcludesItemsWithoutTheMeasure() {
    RawEvent withMeasure = event("wi:1", "id-ana", Map.of("hours", "3"));
    RawEvent withoutMeasure = event("wi:2", "id-bruno", Map.of());
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(withMeasure, withoutMeasure),
            def("hours", Aggregation.MEDIAN),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    assertThat(items)
        .extracting(MetricDrilldownItem::eventId, MetricDrilldownItem::counted)
        .containsExactlyInAnyOrder(tuple("wi:1", true), tuple("wi:2", false));
    assertThat(items.stream().filter(i -> !i.counted()).findFirst().orElseThrow().excludedReason())
        .isEqualTo("sem medida");
  }

  @Test
  void ratioCountsEveryItemWithItsNumDen() {
    RawEvent e1 = event("wi:1", "id-ana", Map.of("num", "1", "den", "1"));
    RawEvent e2 = event("wi:2", "id-bruno", Map.of("num", "0", "den", "1"));
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(e1, e2),
            def(MetricDefinition.VALUE, Aggregation.RATIO),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    assertThat(items).hasSize(2);
    assertThat(items).allMatch(MetricDrilldownItem::counted);
  }

  @Test
  void distinctRatioCountsEveryItem() {
    RawEvent e1 = event("wi:1", "id-ana", Map.of());
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(e1),
            def(MetricDefinition.VALUE, Aggregation.DISTINCT_RATIO),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    assertThat(items).hasSize(1);
    assertThat(items.get(0).counted()).isTrue();
  }

  @Test
  void snapshotCountsOnlyTheLatestEventPerEntity() {
    RawEvent older =
        new RawEvent(
            "wi:1",
            EventType.WORKITEM,
            Instant.parse("2026-06-05T00:00:00Z"),
            null,
            "id-ana",
            null,
            null,
            false,
            Map.of("wip", "1"));
    RawEvent newer =
        new RawEvent(
            "wi:2",
            EventType.WORKITEM,
            Instant.parse("2026-06-20T00:00:00Z"),
            null,
            "id-ana",
            null,
            null,
            false,
            Map.of("wip", "1"));
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(older, newer),
            def("wip", Aggregation.SNAPSHOT),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    assertThat(items)
        .extracting(MetricDrilldownItem::eventId, MetricDrilldownItem::counted)
        .containsExactlyInAnyOrder(tuple("wi:1", false), tuple("wi:2", true));
    assertThat(items.stream().filter(i -> !i.counted()).findFirst().orElseThrow().excludedReason())
        .isEqualTo("superado por evento mais recente da mesma entidade");
  }

  @Test
  void itemCarriesLabelUrlAndFallsBackToIdWhenNoSummary() {
    RawEvent withSummary =
        event("wi:1", "id-ana", Map.of("summary", "Corrige bug X", "url", "https://ado/wi/1"));
    RawEvent withoutSummary = event("wi:2", "id-bruno", Map.of());
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(withSummary, withoutSummary),
            def(MetricDefinition.VALUE, Aggregation.SUM),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    MetricDrilldownItem i1 =
        items.stream().filter(i -> i.eventId().equals("wi:1")).findFirst().orElseThrow();
    assertThat(i1.label()).isEqualTo("Corrige bug X");
    assertThat(i1.url()).isEqualTo("https://ado/wi/1");
    MetricDrilldownItem i2 =
        items.stream().filter(i -> i.eventId().equals("wi:2")).findFirst().orElseThrow();
    assertThat(i2.label()).isEqualTo("wi:2");
    assertThat(i2.url()).isEmpty();
  }

  @Test
  void itemsOutsideTheBucketAreExcluded() {
    RawEvent inBucket = event("wi:1", "id-ana", Map.of());
    RawEvent outOfBucket =
        new RawEvent(
            "wi:2",
            EventType.WORKITEM,
            Instant.parse("2026-05-15T00:00:00Z"),
            null,
            "id-ana",
            null,
            null,
            false,
            Map.of());
    List<MetricDrilldownItem> items =
        MetricsEngine.items(
            index(),
            List.of(inBucket, outOfBucket),
            def(MetricDefinition.VALUE, Aggregation.SUM),
            "all",
            new Period(Frequency.MONTHLY, BUCKET_START),
            e -> true);

    assertThat(items).extracting(MetricDrilldownItem::eventId).containsExactly("wi:1");
  }
}
