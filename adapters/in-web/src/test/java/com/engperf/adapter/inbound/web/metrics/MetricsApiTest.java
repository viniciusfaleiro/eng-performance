package com.engperf.adapter.inbound.web.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.adapter.inbound.web.auth.AuthWebExceptionHandler;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.metrics.MetricCard;
import com.engperf.application.metrics.MetricDrilldownItem;
import com.engperf.application.metrics.MetricSeries;
import com.engperf.application.metrics.SeriesPoint;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Node-scoped metrics API: in-scope 200, out-of-scope 403, coaching-only, frequency passthrough.
 */
class MetricsApiTest {

  private static final MetricDefinition DEF =
      new MetricDefinition(
          "throughput",
          "Throughput",
          "fluxo",
          EventType.PR,
          AttributionScope.PERSON,
          Aggregation.SUM,
          "PRs",
          Direction.HIGHER_BETTER);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new MetricsController(new FakeMetrics()))
            .setControllerAdvice(new AuthWebExceptionHandler())
            .build();
  }

  private static AuthenticatedUser user(AccessScope scope) {
    return new AuthenticatedUser(
        new UserAccount("u:x", "X", "x@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:bruno", "h"),
        scope);
  }

  private static AccessScope admin() {
    return new AccessScope(true, true, Set.of(), Set.of(), Set.of());
  }

  private static AccessScope member() {
    // Member of t:checkout; sees own individual only.
    return new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:bruno"));
  }

  @Test
  void adminGetsCardsInScope() throws Exception {
    mvc.perform(
            get("/api/metrics/cards?node=all&freq=Semanal")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("throughput"))
        .andExpect(jsonPath("$[0].coveragePct").value(90.0));
  }

  @Test
  void memberDeniedOnAnotherTeam() throws Exception {
    mvc.perform(
            get("/api/metrics/cards?node=t:payments&freq=Semanal")
                .requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/metrics/cards?node=t:checkout&freq=Semanal")
                .requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isOk());
  }

  @Test
  void peerIndividualSeriesDenied() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/series?node=p:ana")
                .requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/metrics/throughput/series?node=p:bruno")
                .requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isOk());
  }

  @Test
  void frequencyChangesTheResult() throws Exception {
    // FakeMetrics encodes the frequency ordinal into the value, so different freq → different
    // value.
    mvc.perform(
            get("/api/metrics/cards?node=all&freq=Diário").requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(jsonPath("$[0].value").value((double) Frequency.DAILY.ordinal()));
    mvc.perform(
            get("/api/metrics/cards?node=all&freq=Mensal").requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(jsonPath("$[0].value").value((double) Frequency.MONTHLY.ordinal()));
  }

  @Test
  void itemsReturnsTheListForANodeInScope() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=p:bruno")
                .requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].eventId").value("pr:1"))
        .andExpect(jsonPath("$[0].eventType").value("pr"))
        .andExpect(jsonPath("$[0].counted").value(true));
  }

  @Test
  void itemsDeniedForNodeOutsideScope() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=t:payments")
                .requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isForbidden());
  }

  @Test
  void itemsWithoutBucketDefaultsToTheCurrentPeriod() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all").requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("bucketStart=null"));
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&bucket=2026-05-01")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(jsonPath("$[0].label").value("bucketStart=2026-05-01"));
  }

  @Test
  void catalogNeedsNoScope() throws Exception {
    mvc.perform(get("/api/metrics/catalog").requestAttr(AuthWeb.USER, user(member())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].aggregation").value("sum"));
  }

  private static final class FakeMetrics implements MetricsQueryUseCase {
    @Override
    public List<MetricDefinition> catalog() {
      return List.of(DEF);
    }

    @Override
    public List<MetricCard> cards(String nodeId, Frequency frequency) {
      MetricValue v = MetricValue.of(frequency.ordinal(), null, DEF.direction());
      return List.of(new MetricCard(DEF, v, new Coverage(9, 10)));
    }

    @Override
    public MetricSeries series(String metricKey, String nodeId, Frequency frequency) {
      MetricValue v = MetricValue.of(frequency.ordinal(), null, DEF.direction());
      return new MetricSeries(DEF, List.of(new SeriesPoint("2026-06-01", v)), new Coverage(9, 10));
    }

    @Override
    public MetricSeries cohortSeries(
        String metricKey, String nodeId, Frequency frequency, boolean aiAssisted) {
      MetricValue v = MetricValue.of(frequency.ordinal(), null, DEF.direction());
      return new MetricSeries(DEF, List.of(new SeriesPoint("2026-06-01", v)), new Coverage(9, 10));
    }

    @Override
    public List<MetricDrilldownItem> items(
        String metricKey, String nodeId, Frequency frequency, String bucketStart) {
      return List.of(
          new MetricDrilldownItem(
              "pr:1",
              EventType.PR,
              "https://ado/pr/1",
              "bucketStart=" + bucketStart, // echoes the param so the test can assert it
              nodeId,
              Instant.parse("2026-06-10T10:00:00Z"),
              1.0,
              true,
              null));
    }
  }
}
