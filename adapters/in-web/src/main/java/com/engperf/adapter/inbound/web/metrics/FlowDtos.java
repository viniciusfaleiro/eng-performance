package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.metrics.FlowCard;
import com.engperf.application.metrics.FlowDashboard;
import com.engperf.application.metrics.ImpedimentReasons;
import com.engperf.application.metrics.ImpedimentSlice;
import com.engperf.application.metrics.PhaseSlice;
import com.engperf.application.metrics.ScatterPoint;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Response payloads for the composed Fluxo dashboard. */
public final class FlowDtos {

  private FlowDtos() {}

  public record FlowCardDto(
      String key,
      String label,
      String unit,
      String direction,
      double value,
      Double changePct,
      String sentiment,
      double coveragePct,
      boolean filterableByContainer,
      boolean filtered,
      long untypedExcluded) {

    public static FlowCardDto from(FlowCard c) {
      MetricDefinition d = c.definition();
      MetricValue v = c.value();
      return new FlowCardDto(
          d.key(),
          d.label(),
          d.unit(),
          d.direction().name().toLowerCase(Locale.ROOT),
          v.value(),
          v.changePct(),
          v.sentiment().name().toLowerCase(Locale.ROOT),
          c.coverage().percent(),
          d.filterableByContainer(),
          c.filtered(),
          c.untypedExcluded());
    }
  }

  /**
   * Uma fase com as duas leituras: as horas e a participação dela no ciclo. A participação é a
   * pergunta ("quantos por cento do nosso tempo é espera?") e as horas são o que a torna
   * conferível.
   */
  public record PhaseDto(String key, String label, double hours, double sharePct) {

    public static PhaseDto from(PhaseSlice p) {
      return new PhaseDto(p.key(), p.label(), p.hours(), p.sharePct());
    }
  }

  /**
   * One part of the waiting time: a reason, the reserved bucket for impeded time nobody named, or
   * the residual of waiting that carried no signal. {@code share} is of {@code totalHours} in
   * {@link ImpedimentsDto} — never of the waiting card, which is a median.
   */
  public record ImpedimentSliceDto(String key, String label, double hours, double share) {

    public static ImpedimentSliceDto from(ImpedimentSlice s) {
      return new ImpedimentSliceDto(s.key(), s.label(), s.hours(), s.share());
    }
  }

  /**
   * {@code totalHours} é a <strong>Espera exibida no board</strong> — o número do qual a gaveta foi
   * aberta, e a soma exata das fatias. {@code populationHours} é a soma bruta das horas de espera
   * da população, outra pergunta, que a tela mostra como contexto e nunca como total das fatias.
   *
   * <p>{@code items}/{@code itemsWithSignal} é a cobertura do sinal. Sem ela, um time que nunca
   * marca impedimento se lê igual a um time que nunca travou.
   */
  public record ImpedimentsDto(
      List<ImpedimentSliceDto> slices,
      double totalHours,
      double populationHours,
      int items,
      int itemsWithSignal) {

    public static ImpedimentsDto from(ImpedimentReasons r) {
      return new ImpedimentsDto(
          r.slices().stream().map(ImpedimentSliceDto::from).toList(),
          r.waitingHours(),
          r.populationHours(),
          r.items(),
          r.itemsWithSignal());
    }
  }

  public record ScatterDto(String nodeId, String label, double throughput, double cycleTime) {

    public static ScatterDto from(ScatterPoint s) {
      return new ScatterDto(s.nodeId(), s.label(), s.throughput(), s.cycleTime());
    }
  }

  public record FlowDashboardDto(
      String nodeId,
      String childType,
      List<FlowCardDto> cards,
      List<PhaseDto> phases,
      ImpedimentsDto impediments,
      List<ScatterDto> scatter) {

    /** Builds the payload, dropping any scatter point the caller may not view. */
    public static FlowDashboardDto from(FlowDashboard dash, Predicate<String> canView) {
      return new FlowDashboardDto(
          dash.nodeId(),
          dash.childType(),
          dash.cards().stream().map(FlowCardDto::from).toList(),
          dash.phases().stream().map(PhaseDto::from).toList(),
          ImpedimentsDto.from(dash.impediments()),
          dash.scatter().stream()
              .filter(s -> canView.test(s.nodeId()))
              .map(ScatterDto::from)
              .toList());
    }
  }
}
