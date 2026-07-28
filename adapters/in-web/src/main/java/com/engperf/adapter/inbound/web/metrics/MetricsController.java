package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.CardDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.CatalogItemDto;
import com.engperf.adapter.inbound.web.metrics.MetricsDtos.SeriesDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
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

  public MetricsController(MetricsQueryUseCase metrics) {
    this.metrics = metrics;
  }

  @GetMapping("/api/metrics/catalog")
  public List<CatalogItemDto> catalog() {
    return metrics.catalog().stream().map(CatalogItemDto::from).toList();
  }

  @GetMapping("/api/metrics/cards")
  public List<CardDto> cards(
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return metrics.cards(node, frequency(freq)).stream().map(CardDto::from).toList();
  }

  @GetMapping("/api/metrics/{key}/series")
  public SeriesDto series(
      @PathVariable String key,
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    requireView(user, node);
    return SeriesDto.from(metrics.series(key, node, frequency(freq)));
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
