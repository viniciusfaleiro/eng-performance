package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.CardDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.CatalogItemDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.DrilldownItemDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.EntityShareDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.ExplanationsDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.PeriodDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.SeriesDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
import java.util.List;
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
  public PeriodDto period(PeriodQuery selection) {
    return PeriodDto.from(
        periods.resolve(selection.toRequest()), periods.resolve(selection.currentRequest()));
  }

  @GetMapping("/api/metrics/cards")
  public List<CardDto> cards(
      @RequestParam(defaultValue = "all") String node,
      PeriodQuery selection,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return metrics.cards(node, periods.resolve(selection.toRequest())).stream()
        .map(CardDto::from)
        .toList();
  }

  @GetMapping("/api/metrics/{key}/series")
  public SeriesDto series(
      @PathVariable String key,
      @RequestParam(defaultValue = "all") String node,
      PeriodQuery selection,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return SeriesDto.from(metrics.series(key, node, periods.resolve(selection.toRequest())));
  }

  @GetMapping("/api/metrics/{key}/items")
  public List<DrilldownItemDto> items(
      @PathVariable String key,
      @RequestParam(defaultValue = "all") String node,
      PeriodQuery selection,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return metrics.items(key, node, periods.resolve(selection.toRequest())).stream()
        .map(DrilldownItemDto::from)
        .toList();
  }

  /**
   * Who is behind a metric counted in people. Each person is checked individually — the aggregate
   * card stays as it is, so the number and this list may cover different populations, which is
   * correct: the number is org-wide, the list is nominal and coaching-scoped.
   */
  @GetMapping("/api/metrics/{key}/people")
  public List<EntityShareDto> people(
      @PathVariable String key,
      @RequestParam(defaultValue = "all") String node,
      PeriodQuery selection,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return metrics.entityShares(key, node, periods.resolve(selection.toRequest())).stream()
        .filter(s -> user.scope().canView(s.entityId()))
        .map(EntityShareDto::from)
        .toList();
  }

  private static void requireView(AuthenticatedUser user, String node) {
    if (!user.scope().canView(node)) {
      throw new ForbiddenException("node outside access scope: " + node);
    }
  }

  /** Accepts the prototype's PT labels (Diário/Semanal/Mensal) or the enum names. */
}
