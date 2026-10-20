package com.engperf.application.port.inbound;

import com.engperf.application.metrics.AiCard;
import com.engperf.application.metrics.AiDashboard;
import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;

public interface AiDashboardUseCase {

  default AiDashboard dashboard(String nodeId, Period period) {
    return dashboard(nodeId, period, WorkTypeFilter.none());
  }

  default AiDashboard dashboard(String nodeId, Period period, WorkTypeFilter workTypes) {
    return dashboard(nodeId, period, workTypes, ContainerFilter.none());
  }

  AiDashboard dashboard(
      String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers);

  default AiCard impact(String nodeId, Period period) {
    return impact(nodeId, period, WorkTypeFilter.none());
  }

  default AiCard impact(String nodeId, Period period, WorkTypeFilter workTypes) {
    return impact(nodeId, period, workTypes, ContainerFilter.none());
  }

  AiCard impact(String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers);
}
