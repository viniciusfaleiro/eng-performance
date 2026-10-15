package com.engperf.application.port.inbound;

import com.engperf.application.metrics.AiCard;
import com.engperf.application.metrics.AiDashboard;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;

public interface AiDashboardUseCase {

  default AiDashboard dashboard(String nodeId, Period period) {
    return dashboard(nodeId, period, WorkTypeFilter.none());
  }

  AiDashboard dashboard(String nodeId, Period period, WorkTypeFilter workTypes);

  default AiCard impact(String nodeId, Period period) {
    return impact(nodeId, period, WorkTypeFilter.none());
  }

  AiCard impact(String nodeId, Period period, WorkTypeFilter workTypes);
}
