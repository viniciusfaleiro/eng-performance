package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.metrics.MetricsDtos.SeriesDto;
import com.engperf.application.metrics.ActivityItem;
import com.engperf.application.metrics.CalendarDay;
import com.engperf.application.metrics.ConventionFlag;
import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.application.metrics.ReviewStats;
import com.engperf.application.metrics.WorkTypeSlice;
import java.util.List;

/** Response payloads for the composed individual (person) contribution panel. */
public final class IndividualDtos {

  private IndividualDtos() {}

  public record CalendarDayDto(String date, int count) {
    public static CalendarDayDto from(CalendarDay c) {
      return new CalendarDayDto(c.date(), c.count());
    }
  }

  public record ReviewStatsDto(
      int commentsGiven,
      int approvalsGiven,
      int rejectionsGiven,
      int reviewsGiven,
      int reviewsReceived) {
    public static ReviewStatsDto from(ReviewStats r) {
      return new ReviewStatsDto(
          r.commentsGiven(),
          r.approvalsGiven(),
          r.rejectionsGiven(),
          r.reviewsGiven(),
          r.reviewsReceived());
    }
  }

  public record WorkTypeDto(String type, String label, double hours, double sharePct) {
    public static WorkTypeDto from(WorkTypeSlice w) {
      return new WorkTypeDto(w.type(), w.label(), w.hours(), w.sharePct());
    }
  }

  public record ActivityDto(String kind, String summary, String repo, String date, String url) {
    public static ActivityDto from(ActivityItem a) {
      return new ActivityDto(a.kind(), a.summary(), a.repo(), a.date(), a.url());
    }
  }

  public record ConventionFlagDto(
      String code,
      String severity,
      String reference,
      String title,
      String detail,
      List<String> metrics) {
    public static ConventionFlagDto from(ConventionFlag f) {
      return new ConventionFlagDto(
          f.code(), f.severity(), f.reference(), f.title(), f.detail(), f.metrics());
    }
  }

  public record IndividualDashboardDto(
      String nodeId,
      String label,
      double assertivenessPct,
      List<CalendarDayDto> calendar,
      List<SeriesDto> delivery,
      ReviewStatsDto reviews,
      List<WorkTypeDto> workTypes,
      List<ActivityDto> activity,
      List<ConventionFlagDto> conventions) {

    public static IndividualDashboardDto from(IndividualDashboard d) {
      return new IndividualDashboardDto(
          d.nodeId(),
          d.label(),
          d.assertivenessPct(),
          d.calendar().stream().map(CalendarDayDto::from).toList(),
          d.delivery().stream().map(SeriesDto::from).toList(),
          ReviewStatsDto.from(d.reviews()),
          d.workTypes().stream().map(WorkTypeDto::from).toList(),
          d.activity().stream().map(ActivityDto::from).toList(),
          d.conventions().stream().map(ConventionFlagDto::from).toList());
    }
  }
}
