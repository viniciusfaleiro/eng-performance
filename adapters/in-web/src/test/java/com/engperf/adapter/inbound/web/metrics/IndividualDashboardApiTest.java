package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.application.metrics.ReviewStats;
import com.engperf.application.metrics.WorkTypeSlice;
import com.engperf.application.port.inbound.IndividualDashboardUseCase;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.Frequency;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Individual panel API: coaching-only — own/managing/admin get 200, others 403. */
class IndividualDashboardApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new IndividualDashboardController(new FakeIndividual()))
            .setControllerAdvice(new AuthWebExceptionHandler())
            .build();
  }

  private static AuthenticatedUser user(AccessScope scope) {
    return new AuthenticatedUser(
        new UserAccount("u:x", "X", "x@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "h"),
        scope);
  }

  @Test
  void managingAccountGetsThePanel() throws Exception {
    AccessScope manager =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));
    mvc.perform(get("/api/individuals/p:ana?freq=Mensal").requestAttr(AuthWeb.USER, user(manager)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assertivenessPct").value(66.0))
        .andExpect(jsonPath("$.reviews.reviewsGiven").value(5))
        .andExpect(jsonPath("$.workTypes[0].type").value("feature"))
        .andExpect(jsonPath("$.calendar").isArray());
  }

  @Test
  void adminGetsThePanel() throws Exception {
    AccessScope admin = new AccessScope(true, true, Set.of(), Set.of(), Set.of());
    mvc.perform(get("/api/individuals/p:ana?freq=Mensal").requestAttr(AuthWeb.USER, user(admin)))
        .andExpect(status().isOk());
  }

  @Test
  void nonManagingAccountDenied() throws Exception {
    AccessScope exec = new AccessScope(false, true, Set.of(), Set.of(), Set.of());
    mvc.perform(get("/api/individuals/p:ana?freq=Mensal").requestAttr(AuthWeb.USER, user(exec)))
        .andExpect(status().isForbidden());
  }

  private static final class FakeIndividual implements IndividualDashboardUseCase {
    @Override
    public IndividualDashboard dashboard(String personNodeId, Frequency frequency) {
      return new IndividualDashboard(
          personNodeId,
          "Ana",
          66.0,
          List.of(),
          List.of(),
          new ReviewStats(40, 3, 1, 5, 2),
          List.of(new WorkTypeSlice("feature", "Feature", 10.0, 50.0)),
          List.of());
    }
  }
}
