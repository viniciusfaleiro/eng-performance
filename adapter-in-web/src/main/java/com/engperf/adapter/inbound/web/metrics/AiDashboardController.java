package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.AiDtos.AiDashboardDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.AiDashboard;
import com.engperf.application.port.inbound.AiDashboardUseCase;
import com.engperf.domain.metrics.Frequency;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composed IA dashboard endpoint. The base node is checked against the caller's S2 access scope
 * (403 outside scope); adoption ranking rows are additionally filtered to nodes the caller may
 * view, and the use-case never compares people (coaching-only).
 */
@RestController
public class AiDashboardController {

  private final AiDashboardUseCase ai;

  public AiDashboardController(AiDashboardUseCase ai) {
    this.ai = ai;
  }

  @GetMapping("/api/dashboards/ai")
  public AiDashboardDto dashboard(
      @RequestParam(defaultValue = "all") String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    if (!user.scope().canView(node)) {
      throw new ForbiddenException("node outside access scope: " + node);
    }
    AiDashboard dash = ai.dashboard(node, frequency(freq));
    return AiDashboardDto.from(dash, user.scope()::canView);
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
