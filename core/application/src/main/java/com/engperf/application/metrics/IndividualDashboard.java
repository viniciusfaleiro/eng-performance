package com.engperf.application.metrics;

import java.util.List;

/**
 * The composed individual (person) contribution panel: the commit calendar, PR assertiveness, the
 * reused delivery series (throughput, cycle time, %-with-AI), the code-review contribution, the
 * work distribution, recent activity for the drawer, and convention-adherence flags. Coaching-only
 * — never aggregated.
 */
public record IndividualDashboard(
    String nodeId,
    String label,
    double assertivenessPct,
    List<CalendarDay> calendar,
    List<MetricSeries> delivery,
    ReviewStats reviews,
    WorkDistribution distribution,
    List<ActivityItem> activity,
    List<ConventionFlag> conventions,
    PlatformAccess access) {

  /**
   * Acesso da pessoa à plataforma. Fica fora da lista de métricas de propósito: mede adesão a uma
   * ferramenta, não trabalho de engenharia, e misturar as duas coisas convidaria a ler "não acessa
   * há 20 dias" como parte do desempenho.
   *
   * @param hasAccount se a pessoa tem conta; sem conta não há o que afirmar
   * @param lastLoginAt quando acessou pela última vez, ou {@code null} para quem nunca acessou
   */
  public record PlatformAccess(boolean hasAccount, java.time.Instant lastLoginAt) {

    public static final PlatformAccess NO_ACCOUNT = new PlatformAccess(false, null);
  }

  public IndividualDashboard {
    calendar = List.copyOf(calendar);
    delivery = List.copyOf(delivery);
    activity = List.copyOf(activity);
    conventions = List.copyOf(conventions);
  }
}
