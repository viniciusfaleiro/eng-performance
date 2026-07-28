package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.AdoptionRank;
import com.engperf.application.metrics.AiCard;
import com.engperf.application.metrics.AiDashboard;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Response payloads for the composed IA dashboard. */
public final class AiDtos {

  private AiDtos() {}

  public record AiCardDto(
      String key,
      String label,
      String unit,
      String direction,
      double value,
      Double changePct,
      String sentiment,
      double coveragePct) {

    public static AiCardDto from(AiCard c) {
      MetricDefinition d = c.definition();
      MetricValue v = c.value();
      return new AiCardDto(
          d.key(),
          d.label(),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT),
          v.value(),
          v.changePct(),
          v.sentiment().name().toLowerCase(Locale.ROOT),
          c.coverage().percent());
    }
  }

  public record AdoptionDto(String nodeId, String label, double adoption) {

    public static AdoptionDto from(AdoptionRank r) {
      return new AdoptionDto(r.nodeId(), r.label(), r.adoption());
    }
  }

  public record AiDashboardDto(
      String nodeId,
      String childType,
      List<AiCardDto> cards,
      List<AdoptionDto> adoption,
      List<Double> cycleWithAi,
      List<Double> cycleWithoutAi) {

    /** Builds the payload, dropping any ranked child the caller may not view. */
    public static AiDashboardDto from(AiDashboard dash, Predicate<String> canView) {
      return new AiDashboardDto(
          dash.nodeId(),
          dash.childType(),
          dash.cards().stream().map(AiCardDto::from).toList(),
          dash.adoption().stream()
              .filter(r -> canView.test(r.nodeId()))
              .map(AdoptionDto::from)
              .toList(),
          dash.cycleWithAi(),
          dash.cycleWithoutAi());
    }
  }
}
