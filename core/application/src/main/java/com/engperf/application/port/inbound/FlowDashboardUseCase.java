package com.engperf.application.port.inbound;

import com.engperf.application.metrics.FlowDashboard;
import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;

public interface FlowDashboardUseCase {

  default FlowDashboard dashboard(String nodeId, Period period) {
    return dashboard(nodeId, period, WorkTypeFilter.none());
  }

  /** The dashboard read under a work-type selection; an empty one reads everything. */
  default FlowDashboard dashboard(String nodeId, Period period, WorkTypeFilter workTypes) {
    return dashboard(nodeId, period, workTypes, ContainerFilter.none());
  }

  /** As above, also restricted to one side of the container/leaf line. */
  FlowDashboard dashboard(
      String nodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers);
}
