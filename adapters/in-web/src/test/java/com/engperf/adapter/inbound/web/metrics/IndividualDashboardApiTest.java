package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.application.metrics.PeriodResolver;
import com.engperf.application.metrics.ReviewEntry;
import com.engperf.application.metrics.ReviewStats;
import com.engperf.application.metrics.WorkDistribution;
import com.engperf.application.metrics.WorkItemEntry;
import com.engperf.application.metrics.WorkTypeSlice;
import com.engperf.application.port.inbound.IndividualDashboardUseCase;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.Period;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Individual panel API: coaching-only — own/managing/admin get 200, others 403. */
class IndividualDashboardApiTest {

  private static final PeriodResolverUseCase PERIODS =
      new PeriodResolver(Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC));

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new IndividualDashboardController(new FakeIndividual(), PERIODS))
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

  /**
   * As listas viajam no payload do painel, e não num endpoint próprio: vêm do mesmo cálculo que
   * produziu o número exibido, então não há como divergirem dele. As duas horas por item estão
   * presentes porque uma sozinha não é conferível.
   */
  @Test
  void theCountedItemsTravelWithTheirTwoHourFigures() throws Exception {
    AccessScope admin = new AccessScope(true, true, Set.of(), Set.of(), Set.of());
    mvc.perform(get("/api/individuals/p:ana?freq=Mensal").requestAttr(AuthWeb.USER, user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workTypes[0].itemCount").value(2))
        .andExpect(jsonPath("$.workTypes[0].items[0].id").value("wi:69271"))
        .andExpect(jsonPath("$.workTypes[0].items[0].elapsedHours").value(30.0))
        .andExpect(jsonPath("$.workTypes[0].items[0].countedHours").value(6.0))
        .andExpect(jsonPath("$.workTypes[0].items[0].url").value(WI_URL))
        // A coluna contabilizada é a que fecha com a fatia; a corrida pode passar do período.
        .andExpect(jsonPath("$.workTypes[0].hours").value(10.0))
        .andExpect(jsonPath("$.containersExcluded").value(3));
  }

  /** A review não tem registro próprio no ADO, então o link é o da pull request. */
  @Test
  void bothReviewDirectionsCarryTheirReviewsWithAPullRequestLink() throws Exception {
    AccessScope admin = new AccessScope(true, true, Set.of(), Set.of(), Set.of());
    mvc.perform(get("/api/individuals/p:ana?freq=Mensal").requestAttr(AuthWeb.USER, user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviews.given[0].decision").value("approved"))
        .andExpect(jsonPath("$.reviews.given[0].url").value(PR_URL))
        .andExpect(jsonPath("$.reviews.received[0].decision").value("changes_requested"))
        .andExpect(jsonPath("$.reviews.received[0].url").value(PR_URL))
        // Recebidas listam PRs: nenhum campo nomeia quem revisou.
        .andExpect(jsonPath("$.reviews.received[0].reviewer").doesNotExist());
  }

  /** Quem não pode ver a pessoa não recebe as listas junto — o escopo recorta o payload inteiro. */
  @Test
  void aDeniedRequestCarriesNoLists() throws Exception {
    AccessScope exec = new AccessScope(false, true, Set.of(), Set.of(), Set.of());
    mvc.perform(get("/api/individuals/p:ana?freq=Mensal").requestAttr(AuthWeb.USER, user(exec)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.workTypes").doesNotExist())
        .andExpect(jsonPath("$.reviews").doesNotExist());
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

  private static final String PR_URL = "https://dev.azure.com/org/proj/_git/repo/pullrequest/7";
  private static final String WI_URL = "https://dev.azure.com/org/proj/_workitems/edit/69271";

  private static final class FakeIndividual implements IndividualDashboardUseCase {
    @Override
    public IndividualDashboard dashboard(String personNodeId, Period period) {
      return new IndividualDashboard(
          personNodeId,
          "Ana",
          66.0,
          List.of(),
          List.of(),
          new ReviewStats(
              40,
              3,
              1,
              5,
              2,
              List.of(
                  new ReviewEntry(
                      "review:7:id-ana", "feat: x", PR_URL, "approved", "2026-06-12", 4)),
              List.of(
                  new ReviewEntry(
                      "review:9:id-bruno",
                      "fix: y",
                      PR_URL,
                      "changes_requested",
                      "2026-06-13",
                      1))),
          new WorkDistribution(
              List.of(
                  new WorkTypeSlice(
                      "feature",
                      "Feature",
                      10.0,
                      50.0,
                      2,
                      List.of(
                          new WorkItemEntry("wi:69271", "Ajustar checkout", WI_URL, 30.0, 6.0),
                          new WorkItemEntry("wi:72493", "Revisar cupom", WI_URL, 12.0, 4.0)))),
              3,
              List.of(new WorkItemEntry("wi:74058", "Card de minutos", WI_URL, 0.08, 0.05))),
          List.of(),
          List.of(),
          new IndividualDashboard.PlatformAccess(
              true, java.time.Instant.parse("2026-06-28T09:00:00Z")));
    }
  }
}
