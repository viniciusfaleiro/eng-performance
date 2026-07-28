package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.DoraCard;
import com.engperf.application.metrics.DoraDashboard;
import com.engperf.application.metrics.RankingRow;
import com.engperf.application.port.inbound.DoraDashboardUseCase;
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
import com.engperf.domain.metrics.Tier;
import com.engperf.domain.metrics.TierBands;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** DORA dashboard API: in-scope 200 with tiers+ranking, out-of-scope 403, ranking has no people. */
class DoraDashboardApiTest {

  private static final MetricDefinition LEAD_TIME =
      new MetricDefinition(
          "lead_time",
          "Lead Time for Changes",
          "dora",
          EventType.DEPLOY,
          AttributionScope.REPO,
          Aggregation.MEDIAN,
          MetricDefinition.VALUE,
          "h",
          Direction.LOWER_BETTER,
          new TierBands(24, 168, 720));

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new DoraDashboardController(new FakeDora()))
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
  void adminGetsDashboardWithTierAndRanking() throws Exception {
    mvc.perform(
            get("/api/dashboards/dora?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.childType").value("vertical"))
        .andExpect(jsonPath("$.cards[0].key").value("lead_time"))
        .andExpect(jsonPath("$.cards[0].tier").value("elite"))
        .andExpect(jsonPath("$.ranking[0].nodeId").value("v:pag"));
  }

  @Test
  void outOfScopeNodeDenied() throws Exception {
    // Member of t:checkout only; requesting a foreign vertical is 403.
    AccessScope member =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));
    mvc.perform(
            get("/api/dashboards/dora?node=v:plat&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rankingNeverContainsPeople() throws Exception {
    mvc.perform(
            get("/api/dashboards/dora?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.ranking[*].nodeId")
                .value(
                    org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("p:")))));
  }

  private static final class FakeDora implements DoraDashboardUseCase {
    @Override
    public DoraDashboard dashboard(String nodeId, Frequency frequency) {
      MetricValue v = MetricValue.of(10, 12.0, Direction.LOWER_BETTER); // 10h → Elite
      DoraCard card = new DoraCard(LEAD_TIME, v, Tier.ELITE, new Coverage(9, 10));
      RankingRow pag = new RankingRow("v:pag", "Pagamentos", List.of(card));
      RankingRow plat = new RankingRow("v:plat", "Plataforma", List.of(card));
      return new DoraDashboard(nodeId, "vertical", List.of(card), List.of(pag, plat));
    }
  }
}
