package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.DoraCard;
import com.engperf.application.metrics.DoraDashboard;
import com.engperf.application.metrics.RankingRow;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Response payloads for the composed DORA dashboard. */
public final class DoraDtos {

  private DoraDtos() {}

  public record DoraCardDto(
      String key,
      String label,
      String unit,
      String direction,
      double value,
      Double changePct,
      String sentiment,
      String tier,
      double coveragePct) {

    public static DoraCardDto from(DoraCard c) {
      MetricDefinition d = c.definition();
      MetricValue v = c.value();
      return new DoraCardDto(
          d.key(),
          d.label(),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT),
          v.value(),
          v.changePct(),
          v.sentiment().name().toLowerCase(Locale.ROOT),
          c.tierOptional().map(t -> t.name().toLowerCase(Locale.ROOT)).orElse(null),
          c.coverage().percent());
    }
  }

  public record RankingRowDto(String nodeId, String label, List<DoraCardDto> cards) {

    public static RankingRowDto from(RankingRow r) {
      return new RankingRowDto(
          r.nodeId(), r.label(), r.cards().stream().map(DoraCardDto::from).toList());
    }
  }

  public record DoraDashboardDto(
      String nodeId, String childType, List<DoraCardDto> cards, List<RankingRowDto> ranking) {

    /**
     * Builds the payload, dropping any ranking row the caller may not view (belt-and-suspenders).
     */
    public static DoraDashboardDto from(DoraDashboard dash, Predicate<String> canView) {
      return new DoraDashboardDto(
          dash.nodeId(),
          dash.childType(),
          dash.cards().stream().map(DoraCardDto::from).toList(),
          dash.ranking().stream()
              .filter(r -> canView.test(r.nodeId()))
              .map(RankingRowDto::from)
              .toList());
    }
  }
}
