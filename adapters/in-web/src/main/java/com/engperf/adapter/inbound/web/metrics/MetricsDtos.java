package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.EntityShare;
import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricDrilldownItem;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.application.metrics.SeriesPoint;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricExplanation;
import com.engperf.domain.metrics.MetricValue;
import com.engperf.domain.metrics.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Response payloads for the metrics endpoints. */
public final class MetricsDtos {

  private MetricsDtos() {}

  public record CatalogItemDto(
      String key,
      String label,
      String group,
      String scope,
      String aggregation,
      String unit,
      String direction,
      ExplanationDto explanation) {

    public static CatalogItemDto from(MetricDefinition d) {
      return new CatalogItemDto(
          d.key(),
          d.label(),
          d.group(),
          d.scope().name().toLowerCase(Locale.ROOT),
          d.aggregation().name().toLowerCase(Locale.ROOT),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT),
          d.explained().map(ExplanationDto::from).orElse(null));
    }
  }

  /**
   * The resolved period and the one the system considers current — {@code start} is the first day
   * of the bucket, which is also what the API accepts back.
   */
  /**
   * The period a request was actually computed for, plus what it is compared against.
   *
   * <p>{@code days} is here so the browser stops deriving it from the frequency. That derivation
   * was a second copy of a rule the engine already owns — correct only while every period was a
   * bucket, and wrong for any chosen range — and duplicated rules of that kind have cost us bugs
   * before.
   *
   * <p>{@code bucket} says whether the period is a calendar bucket, which is what the UI needs to
   * know to label the comparison honestly: a bucket compares against the previous bucket, anything
   * else against the dates in {@code previousStart}/{@code previousEnd}.
   *
   * @param end exclusive, as the engine reads it — the day after the last day included
   */
  public record PeriodDto(
      String start,
      String end,
      String currentStart,
      boolean current,
      long days,
      boolean bucket,
      String previousStart,
      String previousEnd) {

    public static PeriodDto from(Period resolved, Period current) {
      Period previous = resolved.previous();
      return new PeriodDto(
          resolved.start().toString(),
          resolved.end().toString(),
          current.start().toString(),
          resolved.equals(current),
          resolved.days(),
          resolved.isBucket(),
          previous.start().toString(),
          previous.end().toString());
    }
  }

  /**
   * One person's contribution to a metric counted in people. Carries both counts, not only the
   * share: 1-of-2 and 40-of-80 are the same percentage and very different situations.
   */
  public record EntityShareDto(
      String personId, String label, long matching, long total, double share) {

    public static EntityShareDto from(EntityShare s) {
      return new EntityShareDto(s.entityId(), s.label(), s.matching(), s.total(), s.share());
    }
  }

  /** The explanations that do not belong to a single metric, plus the shared attribution note. */
  public record ExplanationsDto(String attribution, Map<String, ExplanationDto> views) {

    public static ExplanationsDto from(String attribution, Map<String, MetricExplanation> views) {
      Map<String, ExplanationDto> mapped = new LinkedHashMap<>();
      views.forEach((key, value) -> mapped.put(key, ExplanationDto.from(value)));
      return new ExplanationsDto(attribution, mapped);
    }
  }

  /** How the metric is calculated, in reader-facing text — what the "i" icon opens. */
  public record ExplanationDto(
      String rule, String source, String included, String excluded, String example) {

    public static ExplanationDto from(MetricExplanation e) {
      return new ExplanationDto(e.rule(), e.source(), e.included(), e.excluded(), e.example());
    }
  }

  public record ValueDto(double value, Double changePct, String sentiment) {

    public static ValueDto from(MetricValue v) {
      return new ValueDto(v.value(), v.changePct(), v.sentiment().name().toLowerCase(Locale.ROOT));
    }
  }

  public record CardDto(
      String key,
      String label,
      String group,
      String unit,
      String direction,
      double value,
      Double changePct,
      String sentiment,
      double coveragePct) {

    public static CardDto from(MetricCard c) {
      MetricDefinition d = c.definition();
      MetricValue v = c.current();
      return new CardDto(
          d.key(),
          d.label(),
          d.group(),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT),
          v.value(),
          v.changePct(),
          v.sentiment().name().toLowerCase(Locale.ROOT),
          c.coverage().percent());
    }
  }

  public record PointDto(String bucket, double value, Double changePct, String sentiment) {

    public static PointDto from(SeriesPoint p) {
      return new PointDto(
          p.bucketStart(),
          p.value().value(),
          p.value().changePct(),
          p.value().sentiment().name().toLowerCase(Locale.ROOT));
    }
  }

  public record SeriesDto(
      String key,
      String label,
      String unit,
      String direction,
      double coveragePct,
      List<PointDto> points) {

    public static SeriesDto from(MetricSeries s) {
      MetricDefinition d = s.definition();
      Coverage cov = s.coverage();
      return new SeriesDto(
          d.key(),
          d.label(),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT),
          cov.percent(),
          s.points().stream().map(PointDto::from).toList());
    }
  }

  public record DrilldownItemDto(
      String eventId,
      String eventType,
      String url,
      String label,
      String entity,
      String occurredAt,
      double measure,
      boolean counted,
      String excludedReason) {

    public static DrilldownItemDto from(MetricDrilldownItem i) {
      return new DrilldownItemDto(
          i.eventId(),
          i.eventType().name().toLowerCase(Locale.ROOT),
          i.url(),
          i.label(),
          i.entity(),
          i.occurredAt().toString(),
          i.measure(),
          i.counted(),
          i.excludedReason());
    }
  }
}
