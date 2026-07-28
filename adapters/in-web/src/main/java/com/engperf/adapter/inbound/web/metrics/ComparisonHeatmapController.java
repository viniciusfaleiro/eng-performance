package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.ComparisonDtos.ComparisonHeatmapDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.ComparisonHeatmap;
import com.engperf.application.port.inbound.ComparisonHeatmapUseCase;
import com.engperf.domain.metrics.Frequency;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composed Comparativo heatmap endpoint. The base node is checked against the caller's S2 access
 * scope (403 outside scope); structure rows are filtered to nodes the caller may view, and person
 * rows only appear for an admin or the managing/own account (coaching-only).
 */
@RestController
public class ComparisonHeatmapController {

  private final ComparisonHeatmapUseCase comparison;

  public ComparisonHeatmapController(ComparisonHeatmapUseCase comparison) {
    this.comparison = comparison;
  }

  @GetMapping("/api/comparison/heatmap")
  public ComparisonHeatmapDto heatmap(
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestParam(defaultValue = "times") String scope,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    if (!user.scope().canView(node)) {
      throw new ForbiddenException("node outside access scope: " + node);
    }
    ComparisonHeatmap heatmap = comparison.heatmap(node, frequency(freq), scope);
    return ComparisonHeatmapDto.from(heatmap, user.scope());
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
