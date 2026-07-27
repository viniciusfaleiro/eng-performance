package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.AdoptionRank;
import com.engperf.application.metrics.AiCard;
import com.engperf.application.metrics.AiDashboard;
import com.engperf.application.metrics.MetricCatalog;
import com.engperf.application.port.inbound.AiDashboardUseCase;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.Direction;
import com.engperf.domain.metrics.MetricValue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * IA dashboard API: in-scope 200 with cards+adoption+cohort series, out-of-scope 403, no people.
 */
class AiDashboardApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new AiDashboardController(new FakeAi()))
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
  void adminGetsDashboardWithCardsAdoptionAndCohortSeries() throws Exception {
    mvc.perform(
            get("/api/dashboards/ai?node=all&freq=Mensal").requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.childType").value("vertical"))
        .andExpect(jsonPath("$.cards[0].key").value("ai_share"))
        .andExpect(jsonPath("$.cards[2].key").value("ai_impact"))
        .andExpect(jsonPath("$.adoption[0].nodeId").value("v:pag"))
        .andExpect(jsonPath("$.cycleWithAi[0]").value(6.0))
        .andExpect(jsonPath("$.cycleWithoutAi[0]").value(10.0));
  }

  @Test
  void outOfScopeNodeDenied() throws Exception {
    AccessScope member =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));
    mvc.perform(
            get("/api/dashboards/ai?node=v:plat&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adoptionNeverContainsPeople() throws Exception {
    mvc.perform(
            get("/api/dashboards/ai?node=all&freq=Mensal").requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.adoption[*].nodeId")
                .value(
                    org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("p:")))));
  }

  private static final class FakeAi implements AiDashboardUseCase {
    @Override
    public AiDashboard dashboard(String nodeId, com.engperf.domain.metrics.Frequency frequency) {
      List<AiCard> cards =
          List.of(
              new AiCard(
                  sample("ai_share"),
                  MetricValue.of(0.4, null, Direction.HIGHER_BETTER),
                  new com.engperf.domain.metrics.Coverage(4, 5)),
              new AiCard(
                  sample("ai_adoption"),
                  MetricValue.of(0.6, null, Direction.HIGHER_BETTER),
                  new com.engperf.domain.metrics.Coverage(4, 5)),
              new AiCard(
                  MetricCatalog.AI_IMPACT,
                  MetricValue.of(40.0, null, Direction.HIGHER_BETTER),
                  new com.engperf.domain.metrics.Coverage(2, 4)));
      List<AdoptionRank> adoption =
          List.of(
              new AdoptionRank("v:pag", "Pagamentos", 0.6),
              new AdoptionRank("v:plat", "Plataforma", 0.4));
      return new AiDashboard(
          nodeId, "vertical", cards, adoption, List.of(6.0, 6.5), List.of(10.0, 9.5));
    }

    @Override
    public AiCard impact(String nodeId, com.engperf.domain.metrics.Frequency frequency) {
      return new AiCard(
          MetricCatalog.AI_IMPACT,
          MetricValue.of(40.0, null, Direction.HIGHER_BETTER),
          new com.engperf.domain.metrics.Coverage(2, 4));
    }

    private static com.engperf.domain.metrics.MetricDefinition sample(String key) {
      return new com.engperf.domain.metrics.MetricDefinition(
          key,
          key,
          "ia",
          com.engperf.domain.metrics.EventType.COMMIT,
          com.engperf.domain.metrics.AttributionScope.PERSON,
          com.engperf.domain.metrics.Aggregation.RATIO,
          "%",
          Direction.HIGHER_BETTER);
    }
  }
}
