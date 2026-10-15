package com.engperf.application.port.inbound;

import com.engperf.application.metrics.ComparisonHeatmap;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;

public interface ComparisonHeatmapUseCase {

  default ComparisonHeatmap heatmap(String nodeId, Period period, String scope) {
    return heatmap(nodeId, period, scope, WorkTypeFilter.none());
  }

  /** The heatmap under a work-type selection — filterable columns only; the rest say so. */
  ComparisonHeatmap heatmap(String nodeId, Period period, String scope, WorkTypeFilter workTypes);
}
