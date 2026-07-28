package com.engperf.adapter.inbound.web.metrics;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.ForbiddenException;
import com.engperf.adapter.inbound.web.metrics.IndividualDtos.IndividualDashboardDto;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.application.port.inbound.IndividualDashboardUseCase;
import com.engperf.domain.metrics.Frequency;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composed individual (person) panel endpoint. Coaching-only: it serves a person node ({@code p:…})
 * only to an admin or the managing/own account (403 otherwise); an individual is never compared
 * with peers, so no aggregate node is accepted here.
 */
@RestController
public class IndividualDashboardController {

  private final IndividualDashboardUseCase individual;

  public IndividualDashboardController(IndividualDashboardUseCase individual) {
    this.individual = individual;
  }

  @GetMapping("/api/individuals/{node}")
  public IndividualDashboardDto dashboard(
      @PathVariable String node,
      @RequestParam(defaultValue = "Semanal") String freq,
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    if (!node.startsWith("p:") || !user.scope().canViewIndividual(node)) {
      throw new ForbiddenException("individual outside coaching scope: " + node);
    }
    IndividualDashboard dash = individual.dashboard(node, frequency(freq));
    return IndividualDashboardDto.from(dash);
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
