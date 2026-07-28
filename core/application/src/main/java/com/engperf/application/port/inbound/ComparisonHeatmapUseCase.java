package com.engperf.application.port.inbound;

import com.engperf.application.metrics.ComparisonHeatmap;
import com.engperf.domain.metrics.Frequency;

/**
 * Inbound port for the composed Comparativo heatmap. Node-scope and coaching enforcement (403
 * outside scope; people rows only for the managing/own account) lives in the web adapter; this port
 * composes the full children × all-metrics matrix for an already-authorized node.
 */
public interface ComparisonHeatmapUseCase {

  /**
   * @param scope at the overview node, {@code "verticais"} compares verticals, otherwise teams; it
   *     is ignored at every other node, whose child type is structurally determined.
   */
  ComparisonHeatmap heatmap(String nodeId, Frequency frequency, String scope);
}
