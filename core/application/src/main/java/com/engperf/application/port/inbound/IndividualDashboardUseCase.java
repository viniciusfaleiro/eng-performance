package com.engperf.application.port.inbound;

import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.domain.metrics.ContainerFilter;
import com.engperf.domain.metrics.Period;
import com.engperf.domain.metrics.WorkTypeFilter;

public interface IndividualDashboardUseCase {

  default IndividualDashboard dashboard(String personNodeId, Period period) {
    return dashboard(personNodeId, period, WorkTypeFilter.none());
  }

  /**
   * The panel under a work-type selection. The work distribution is never filtered: it answers how
   * the period divided, and a filtered one would always be a single slice at 100%.
   */
  default IndividualDashboard dashboard(
      String personNodeId, Period period, WorkTypeFilter workTypes) {
    return dashboard(personNodeId, period, workTypes, ContainerFilter.none());
  }

  /** As above, also restricted to one side of the container/leaf line. */
  IndividualDashboard dashboard(
      String personNodeId, Period period, WorkTypeFilter workTypes, ContainerFilter containers);
}
