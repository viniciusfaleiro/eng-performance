package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.application.metrics.SeriesPoint;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import java.util.List;
import java.util.Locale;

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
      String direction) {

    public static CatalogItemDto from(MetricDefinition d) {
      return new CatalogItemDto(
          d.key(),
          d.label(),
          d.group(),
          d.scope().name().toLowerCase(Locale.ROOT),
          d.aggregation().name().toLowerCase(Locale.ROOT),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT));
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
}
