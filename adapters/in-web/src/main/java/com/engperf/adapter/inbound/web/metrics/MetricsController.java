package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.CardDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.CatalogItemDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.DrilldownItemDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.ExplanationsDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.PeriodDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.SeriesDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
import com.engperf.domain.metrics.Frequency;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Node-aware metrics API. Every node request is checked against the caller's S2 access scope (403
 * outside scope; individuals coaching-only). The catalog itself carries no node data.
 */
@RestController
public class MetricsController {

  private final MetricsQueryUseCase metrics;
  private final PeriodResolverUseCase periods;

  public MetricsController(MetricsQueryUseCase metrics, PeriodResolverUseCase periods) {
    this.metrics = metrics;
    this.periods = periods;
  }

  @GetMapping("/api/metrics/catalog")
  public List<CatalogItemDto> catalog() {
    return metrics.catalog().stream().map(CatalogItemDto::from).toList();
  }

  /**
   * What the "i" icon opens for charts and panels, plus the attribution note that applies to every
   * metric. Metric explanations travel with the catalog; this covers what has no metric of its own.
   */
  @GetMapping("/api/metrics/explanations")
  public ExplanationsDto explanations() {
    return ExplanationsDto.from(metrics.attributionNote(), metrics.viewExplanations());
  }

  /**
   * The period a request would be computed for. The browser must not decide this on its own: the
   * server's "today" can be pinned ({@code METRICS_REFERENCE_DATE}), and a UI that guessed from the
   * local clock would label a card with a period the engine never used.
   */
  @GetMapping("/api/metrics/period")
  public PeriodDto period(
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestParam(required = false) String period) {
    return PeriodDto.from(
        periods.resolve(frequency(freq), period), periods.resolve(frequency(freq), null));
  }

  @GetMapping("/api/metrics/cards")
  public List<CardDto> cards(
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestParam(required = false) String period,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return metrics.cards(node, periods.resolve(frequency(freq), period)).stream()
        .map(CardDto::from)
        .toList();
  }

  @GetMapping("/api/metrics/{key}/series")
  public SeriesDto series(
      @PathVariable String key,
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestParam(required = false) String period,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return SeriesDto.from(metrics.series(key, node, periods.resolve(frequency(freq), period)));
  }

  @GetMapping("/api/metrics/{key}/items")
  public List<DrilldownItemDto> items(
      @PathVariable String key,
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestParam(required = false) String period,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return metrics.items(key, node, periods.resolve(frequency(freq), period)).stream()
        .map(DrilldownItemDto::from)
        .toList();
  }

  private static void requireView(AuthenticatedUser user, String node) {
    if (!user.scope().canView(node)) {
      throw new ForbiddenException("node outside access scope: " + node);
    }
  }

  /** Accepts the prototype's PT labels (Diário/Semanal/Mensal) or the enum names. */
  private static Frequency frequency(String value) {
    if (value == null) {
      return Frequency.WEEKLY;
    }
    return switch (value.strip().toLowerCase(Locale.ROOT)) {
      case "diário", "diario", "daily" -> Frequency.DAILY;
      case "mensal", "monthly" -> Frequency.MONTHLY;
      default -> Frequency.WEEKLY;
    };
  }
}
