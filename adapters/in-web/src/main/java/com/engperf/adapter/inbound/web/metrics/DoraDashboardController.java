package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.DoraDtos.DoraDashboardDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.DoraDashboard;
import com.engperf.application.port.inbound.DoraDashboardUseCase;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composed DORA dashboard endpoint. The base node is checked against the caller's S2 access scope
 * (403 outside scope); ranking rows are additionally filtered to nodes the caller may view, and the
 * use-case never ranks people (coaching-only).
 */
@RestController
public class DoraDashboardController {

  private final DoraDashboardUseCase dora;
  private final PeriodResolverUseCase periods;

  public DoraDashboardController(DoraDashboardUseCase dora, PeriodResolverUseCase periods) {
    this.dora = dora;
    this.periods = periods;
  }

  @GetMapping("/api/dashboards/dora")
  public DoraDashboardDto dashboard(
      @RequestParam(defaultValue = "all") String node,
      PeriodQuery selection,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    if (!user.scope().canView(node)) {
      throw new ForbiddenException("node outside access scope: " + node);
    }
    DoraDashboard dash = dora.dashboard(node, periods.resolve(selection.toRequest()));
    return DoraDashboardDto.from(dash, user.scope()::canView);
  }

  /** Accepts the prototype's PT labels (Diário/Semanal/Mensal) or the enum names. */
}
