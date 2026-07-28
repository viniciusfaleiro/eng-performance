package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.ComparisonHeatmap;
import com.engperf.application.metrics.HeatmapMetric;
import com.engperf.application.metrics.HeatmapRow;
import com.engperf.application.port.inbound.ComparisonHeatmapUseCase;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.Frequency;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Heatmap API: in-scope 200 with rows+cols, out-of-scope 403, people rows coaching-only. */
class ComparisonHeatmapApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ComparisonHeatmapController(new FakeComparison()))
            .setControllerAdvice(new AuthWebExceptionHandler())
            .build();
  }

  private static AuthenticatedUser user(AccessScope scope) {
    return new AuthenticatedUser(
        new UserAccount("u:x", "X", "x@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "h"),
        scope);
  }

  @Test
  void adminGetsRowsAndColumns() throws Exception {
    AccessScope admin = new AccessScope(true, true, Set.of(), Set.of(), Set.of());
    mvc.perform(
            get("/api/comparison/heatmap?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metrics[0].key").value("deploy_freq"))
        .andExpect(jsonPath("$.rows[*].nodeId").value(Matchers.hasItems("t:core", "p:ana")));
  }

  @Test
  void outOfScopeNodeDenied() throws Exception {
    AccessScope member =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));
    mvc.perform(
            get("/api/comparison/heatmap?node=v:plat&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void orgWideAccountSeesNoPeopleRows() throws Exception {
    AccessScope exec = new AccessScope(false, true, Set.of(), Set.of(), Set.of());
    mvc.perform(
            get("/api/comparison/heatmap?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(exec)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[*].nodeId").value(Matchers.hasItem("t:core")))
        .andExpect(
            jsonPath("$.rows[*].nodeId")
                .value(Matchers.everyItem(Matchers.not(Matchers.startsWith("p:")))));
  }

  @Test
  void managerSeesOwnPeopleButNotOtherStructures() throws Exception {
    AccessScope manager =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));
    mvc.perform(
            get("/api/comparison/heatmap?node=t:checkout&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(manager)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[*].nodeId").value(Matchers.hasItem("p:ana")))
        .andExpect(jsonPath("$.rows[*].nodeId").value(Matchers.not(Matchers.hasItem("t:core"))));
  }

  private static final class FakeComparison implements ComparisonHeatmapUseCase {
    @Override
    public ComparisonHeatmap heatmap(String nodeId, Frequency frequency, String scope) {
      List<HeatmapMetric> metrics =
          List.of(
              new HeatmapMetric("deploy_freq", "Deployment Frequency", "deploys"),
              new HeatmapMetric("ai_impact", "Cycle time mais rápido c/ IA", "%"));
      List<HeatmapRow> rows =
          List.of(
              new HeatmapRow("t:core", "Core", "Time", List.of(3.0, 20.0)),
              new HeatmapRow("p:ana", "Ana", "Pessoa", List.of(1.0, 15.0)));
      return new ComparisonHeatmap(nodeId, metrics, rows);
    }
  }
}
