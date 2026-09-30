package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.metrics.MetricsDtos.SeriesDto;
import com.engperf.application.metrics.ActivityItem;
import com.engperf.application.metrics.CalendarDay;
import com.engperf.application.metrics.ConventionFlag;
import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.application.metrics.ReviewEntry;
import com.engperf.application.metrics.ReviewStats;
import com.engperf.application.metrics.WorkItemEntry;
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

  /**
   * Uma review por trás de uma das contagens. {@code url} aponta para a pull request: a review não
   * tem registro próprio no Azure DevOps, então a PR é o registro dela.
   */
  public record ReviewEntryDto(
      String id, String title, String url, String decision, String date, int comments) {
    public static ReviewEntryDto from(ReviewEntry r) {
      return new ReviewEntryDto(
          r.id(), r.title(), r.url(), r.decision(), r.occurredOn(), r.comments());
    }
  }

  /**
   * As contagens não são limitadas; as listas são. Uma lista cortada com a contagem ao lado ainda
   * diz o quanto está escondendo, que é o mínimo para não enganar.
   */
  public record ReviewStatsDto(
      int commentsGiven,
      int approvalsGiven,
      int rejectionsGiven,
      int reviewsGiven,
      int reviewsReceived,
      List<ReviewEntryDto> given,
      List<ReviewEntryDto> received) {
    public static ReviewStatsDto from(ReviewStats r) {
      return new ReviewStatsDto(
          r.commentsGiven(),
          r.approvalsGiven(),
          r.rejectionsGiven(),
          r.reviewsGiven(),
          r.reviewsReceived(),
          r.given().stream().map(ReviewEntryDto::from).toList(),
          r.received().stream().map(ReviewEntryDto::from).toList());
    }
  }

  /**
   * Um item contabilizado, com as duas horas: {@code elapsedHours} é quanto ficou em andamento
   * dentro do período e {@code countedHours} é o que entrou na conta depois de dividir cada hora
   * corrida entre os itens simultâneos. É a diferença entre as duas que mostra o paralelismo — sem
   * as duas, a conta não é auditável item a item.
   */
  public record WorkItemDto(
      String id, String title, String url, double elapsedHours, double countedHours) {
    public static WorkItemDto from(WorkItemEntry w) {
      return new WorkItemDto(w.id(), w.title(), w.url(), w.elapsedHours(), w.countedHours());
    }
  }

  public record WorkTypeDto(
      String type,
      String label,
      double hours,
      double sharePct,
      int itemCount,
      List<WorkItemDto> items) {
    public static WorkTypeDto from(WorkTypeSlice w) {
      return new WorkTypeDto(
          w.type(),
          w.label(),
          w.hours(),
          w.sharePct(),
          w.itemCount(),
          w.items().stream().map(WorkItemDto::from).toList());
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

  /**
   * Acesso da pessoa à plataforma, à parte das métricas de entrega. {@code hasAccount=false} quer
   * dizer que não há o que mostrar; {@code lastLoginAt=null} com conta quer dizer que ela nunca
   * acessou — que é informação, não dado faltando.
   */
  public record PlatformAccessDto(boolean hasAccount, String lastLoginAt) {

    public static PlatformAccessDto from(IndividualDashboard.PlatformAccess a) {
      return new PlatformAccessDto(
          a.hasAccount(), a.lastLoginAt() == null ? null : a.lastLoginAt().toString());
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
      int containersExcluded,
      List<ActivityDto> activity,
      List<ConventionFlagDto> conventions,
      PlatformAccessDto access) {

    public static IndividualDashboardDto from(IndividualDashboard d) {
      return new IndividualDashboardDto(
          d.nodeId(),
          d.label(),
          d.assertivenessPct(),
          d.calendar().stream().map(CalendarDayDto::from).toList(),
          d.delivery().stream().map(SeriesDto::from).toList(),
          ReviewStatsDto.from(d.reviews()),
          d.distribution().types().stream().map(WorkTypeDto::from).toList(),
          d.distribution().containersExcluded(),
          d.activity().stream().map(ActivityDto::from).toList(),
          d.conventions().stream().map(ConventionFlagDto::from).toList(),
          PlatformAccessDto.from(d.access()));
    }
  }
}
