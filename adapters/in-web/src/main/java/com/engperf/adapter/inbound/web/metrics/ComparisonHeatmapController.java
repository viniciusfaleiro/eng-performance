package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.ComparisonDtos.ComparisonHeatmapDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.ComparisonHeatmap;
import com.engperf.application.port.inbound.ComparisonHeatmapUseCase;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
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
  private final PeriodResolverUseCase periods;

  public ComparisonHeatmapController(
      ComparisonHeatmapUseCase comparison, PeriodResolverUseCase periods) {
    this.comparison = comparison;
    this.periods = periods;
  }

  @GetMapping("/api/comparison/heatmap")
  public ComparisonHeatmapDto heatmap(
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "times") String scope,
      PeriodQuery selection,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    if (!user.scope().canView(node)) {
      throw new ForbiddenException("node outside access scope: " + node);
    }
    ComparisonHeatmap heatmap =
        comparison.heatmap(node, periods.resolve(selection.toRequest()), scope);
    return ComparisonHeatmapDto.from(heatmap, user.scope());
  }

  /** Accepts the prototype's PT labels (Diário/Semanal/Mensal) or the enum names. */
}
