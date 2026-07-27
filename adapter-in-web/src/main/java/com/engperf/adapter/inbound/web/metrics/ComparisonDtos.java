package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.ComparisonHeatmap;
import com.engperf.application.metrics.HeatmapMetric;
import com.engperf.application.metrics.HeatmapRow;
import com.engperf.domain.access.AccessScope;
import java.util.List;

/** Response payloads for the composed Comparativo heatmap. */
public final class ComparisonDtos {

  private ComparisonDtos() {}

  public record HeatmapMetricDto(String key, String label, String unit) {

    public static HeatmapMetricDto from(HeatmapMetric m) {
      return new HeatmapMetricDto(m.key(), m.label(), m.unit());
    }
  }

  public record HeatmapRowDto(String nodeId, String label, String rowType, List<Double> values) {

    public static HeatmapRowDto from(HeatmapRow r) {
      return new HeatmapRowDto(r.nodeId(), r.label(), r.rowType(), r.values());
    }
  }

  public record ComparisonHeatmapDto(
      String nodeId, List<HeatmapMetricDto> metrics, List<HeatmapRowDto> rows) {

    /**
     * Builds the payload, keeping only rows the caller may see: structure rows the caller can view,
     * and person rows only for an admin or the managing/own account (coaching-only).
     */
    public static ComparisonHeatmapDto from(ComparisonHeatmap heatmap, AccessScope scope) {
      return new ComparisonHeatmapDto(
          heatmap.nodeId(),
          heatmap.metrics().stream().map(HeatmapMetricDto::from).toList(),
          heatmap.rows().stream().filter(r -> canSee(scope, r)).map(HeatmapRowDto::from).toList());
    }

    private static boolean canSee(AccessScope scope, HeatmapRow row) {
      return row.nodeId().startsWith("p:")
          ? scope.canViewIndividual(row.nodeId())
          : scope.canView(row.nodeId());
    }
  }
}
