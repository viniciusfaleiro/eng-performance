package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.FlowCard;
import com.engperf.application.metrics.FlowDashboard;
import com.engperf.application.metrics.PhaseSlice;
import com.engperf.application.metrics.ScatterPoint;
import com.engperf.application.port.inbound.FlowDashboardUseCase;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.Aggregation;
import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.Coverage;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.Frequency;
import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricValue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Fluxo dashboard API: in-scope 200 with cards+phases+scatter, out-of-scope 403, no people. */
class FlowDashboardApiTest {

  private static final MetricDefinition CYCLE =
      new MetricDefinition(
          "cycle_time",
          "Cycle Time (código)",
          "fluxo",
          EventType.PR,
          AttributionScope.PERSON,
          Aggregation.MEDIAN,
          "cycle_h",
          "h",
          Direction.LOWER_BETTER,
          null);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new FlowDashboardController(new FakeFlow()))
            .setControllerAdvice(new AuthWebExceptionHandler())
            .build();
  }

  private static AuthenticatedUser user(AccessScope scope) {
    return new AuthenticatedUser(
        new UserAccount("u:x", "X", "x@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "h"),
        scope);
  }

  private static AccessScope admin() {
    return new AccessScope(true, true, Set.of(), Set.of(), Set.of());
  }

  @Test
  void adminGetsDashboardWithCardsPhasesScatter() throws Exception {
    mvc.perform(
            get("/api/dashboards/flow?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.childType").value("vertical"))
        .andExpect(jsonPath("$.cards[0].key").value("cycle_time"))
        .andExpect(jsonPath("$.phases[0].key").value("coding_time"))
        .andExpect(jsonPath("$.scatter[0].nodeId").value("v:pag"));
  }

  @Test
  void outOfScopeNodeDenied() throws Exception {
    AccessScope member =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));
    mvc.perform(
            get("/api/dashboards/flow?node=v:plat&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void scatterNeverContainsPeople() throws Exception {
    mvc.perform(
            get("/api/dashboards/flow?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.scatter[*].nodeId")
                .value(
                    org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("p:")))));
  }

  private static final class FakeFlow implements FlowDashboardUseCase {
    @Override
    public FlowDashboard dashboard(String nodeId, Frequency frequency) {
      MetricValue v = MetricValue.of(9, 10.0, Direction.LOWER_BETTER);
      FlowCard card = new FlowCard(CYCLE, v, new Coverage(9, 10));
      List<PhaseSlice> phases =
          List.of(
              new PhaseSlice("coding_time", "Coding", 5),
              new PhaseSlice("pickup_time", "PR Pickup", 1));
      List<ScatterPoint> scatter =
          List.of(
              new ScatterPoint("v:pag", "Pagamentos", 12, 9),
              new ScatterPoint("v:plat", "Plataforma", 7, 11));
      return new FlowDashboard(nodeId, "vertical", List.of(card), phases, scatter);
    }
  }
}
