package com.engperf.application.metrics;

import java.util.List;

/**
 * The composed individual (person) contribution panel: the commit calendar, PR assertiveness, the
 * reused delivery series (throughput, cycle time, %-with-AI), the code-review contribution, the
 * work-type distribution, and recent activity for the drawer. Coaching-only — never aggregated.
 */
public record IndividualDashboard(
    String nodeId,
    String label,
    double assertivenessPct,
    List<CalendarDay> calendar,
    List<MetricSeries> delivery,
    ReviewStats reviews,
    List<WorkTypeSlice> workTypes,
    List<ActivityItem> activity) {

  public IndividualDashboard {
    calendar = List.copyOf(calendar);
    delivery = List.copyOf(delivery);
    workTypes = List.copyOf(workTypes);
    activity = List.copyOf(activity);
  }
}
