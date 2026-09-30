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
import com.engperf.application.metrics.PeriodResolver;
import com.engperf.application.metrics.SeriesPoint;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.inbound.PeriodResolverUseCase;
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

/**
 * Node-scoped metrics API: in-scope 200, out-of-scope 403, coaching-only, frequency passthrough.
 */
class MetricsApiTest {

  private static final PeriodResolverUseCase PERIODS =
      new PeriodResolver(Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC));

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
        MockMvcBuilders.standaloneSetup(new MetricsController(new FakeMetrics(), PERIODS))
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
  void itemsDefaultToTheCurrentPeriodAndFollowTheChosenOne() throws Exception {
    // Sem parâmetro: a semana que contém o "hoje" do relógio fixo (2026-06-30, uma terça).
    mvc.perform(
            get("/api/metrics/throughput/items?node=all").requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("period=2026-06-29..2026-07-06"));
    // Qualquer data dentro do período resolve para o período — o cliente não precisa arredondar.
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&freq=Mensal&period=2026-05-17")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(jsonPath("$[0].label").value("period=2026-05-01..2026-06-01"));
  }

  /**
   * O intervalo chega ao motor exatamente como foi pedido — a ponta final inclusiva do usuário vira
   * o fim exclusivo do motor, e é aí que um off-by-one apareceria como um dia faltando na conta.
   */
  @Test
  void anExplicitRangeIsPassedThroughAsChosen() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&freq=Mensal&from=2026-03-12&to=2026-06-27")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("period=2026-03-12..2026-06-28"));
  }

  /** A janela móvel é resolvida no servidor: o relógio do navegador não decide o período. */
  @Test
  void aRollingWindowIsResolvedAgainstTheServerClock() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&freq=Semanal&window=7")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("period=2026-06-24..2026-07-01"));
  }

  /** Sem parâmetro de intervalo, nada muda: o balde de calendário de sempre. */
  @Test
  void withoutRangeParametersTheBucketIsUnchanged() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&freq=Mensal")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("period=2026-06-01..2026-07-01"));
  }

  /**
   * Um intervalo impossível é recusado com o motivo legível, em vez de virar um período qualquer.
   * Adivinhar a borda que falta mostraria silenciosamente um período que ninguém pediu.
   */
  @Test
  void anImpossibleOrIncompleteRangeIsRefusedWithAReason() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&from=2026-06-27&to=2026-03-12")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("termina antes")));
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&from=2026-03-12")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("início e fim")));
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&from=12/03/2026&to=2026-06-27")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("AAAA-MM-DD")));
  }

  /** Um intervalo que ainda não começou é recusado como um balde futuro. */
  @Test
  void aFutureRangeIsRefusedLikeAFutureBucket() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&from=2026-12-01&to=2026-12-31")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isBadRequest());
  }

  /**
   * O período resolvido precisa dizer sua duração e contra o que compara. Sem isso o frontend
   * recalcula o divisor por conta própria — uma segunda cópia de uma regra do motor, que só estava
   * certa enquanto todo período era um balde.
   */
  @Test
  void theResolvedPeriodCarriesItsDurationAndBaseline() throws Exception {
    mvc.perform(get("/api/metrics/period?freq=Mensal&period=2026-05-17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.start").value("2026-05-01"))
        .andExpect(jsonPath("$.end").value("2026-06-01"))
        .andExpect(jsonPath("$.days").value(31))
        .andExpect(jsonPath("$.bucket").value(true))
        .andExpect(jsonPath("$.previousStart").value("2026-04-01"))
        .andExpect(jsonPath("$.current").value(false))
        .andExpect(jsonPath("$.currentStart").value("2026-06-01"));

    mvc.perform(get("/api/metrics/period?freq=Mensal&from=2026-03-12&to=2026-06-27"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days").value(108))
        .andExpect(jsonPath("$.bucket").value(false))
        .andExpect(jsonPath("$.previousStart").value("2025-11-24"))
        .andExpect(jsonPath("$.previousEnd").value("2026-03-12"));
  }

  /**
   * Um período futuro não tem resposta correta; um período passado vazio tem, e é zero. Responder
   * zeros para o futuro esconderia a diferença.
   */
  @Test
  void aFuturePeriodIsRefusedAndAnEmptyPastPeriodIsNot() throws Exception {
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&freq=Mensal&period=2026-12-01")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/metrics/throughput/items?node=all&freq=Mensal&period=2019-03-01")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(status().isOk());
  }

  /**
   * O card continua agregando a organização inteira; a lista nominal é recortada pelo escopo
   * individual. Os dois cobrirem populações diferentes é correto, não um bug.
   */
  @Test
  void thePersonBreakdownIsFilteredByIndividualScope() throws Exception {
    AccessScope manager =
        new AccessScope(false, false, Set.of(), Set.of("t:checkout"), Set.of("p:ana"));

    mvc.perform(
            get("/api/metrics/ai_adoption/people?node=t:checkout")
                .requestAttr(AuthWeb.USER, user(manager)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].personId").value("p:ana"))
        .andExpect(jsonPath("$[0].matching").value(0))
        .andExpect(jsonPath("$[0].total").value(3));
  }

  @Test
  void anAdminSeesEveryPersonInTheBreakdown() throws Exception {
    mvc.perform(
            get("/api/metrics/ai_adoption/people?node=all")
                .requestAttr(AuthWeb.USER, user(admin())))
        .andExpect(jsonPath("$.length()").value(2));
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
    public java.util.Map<String, com.engperf.domain.metrics.MetricExplanation> viewExplanations() {
      return java.util.Map.of(
          "trend", new com.engperf.domain.metrics.MetricExplanation("r", "s", "i", "e", "x"));
    }

    @Override
    public String attributionNote() {
      return "as-of-event";
    }

    @Override
    public List<MetricCard> cards(String nodeId, Period period) {
      MetricValue v = MetricValue.of(period.frequency().ordinal(), null, DEF.direction());
      return List.of(new MetricCard(DEF, v, new Coverage(9, 10)));
    }

    @Override
    public MetricSeries series(String metricKey, String nodeId, Period period) {
      MetricValue v = MetricValue.of(period.frequency().ordinal(), null, DEF.direction());
      return new MetricSeries(DEF, List.of(new SeriesPoint("2026-06-01", v)), new Coverage(9, 10));
    }

    @Override
    public MetricSeries cohortSeries(
        String metricKey, String nodeId, Period period, boolean aiAssisted) {
      MetricValue v = MetricValue.of(period.frequency().ordinal(), null, DEF.direction());
      return new MetricSeries(DEF, List.of(new SeriesPoint("2026-06-01", v)), new Coverage(9, 10));
    }

    @Override
    public List<com.engperf.application.metrics.EntityShare> entityShares(
        String metricKey, String nodeId, Period period) {
      return List.of(
          new com.engperf.application.metrics.EntityShare("p:ana", "Ana", 0, 3),
          new com.engperf.application.metrics.EntityShare("p:bruno", "Bruno", 2, 2));
    }

    @Override
    public List<MetricDrilldownItem> items(String metricKey, String nodeId, Period period) {
      return List.of(
          new MetricDrilldownItem(
              "pr:1",
              EventType.PR,
              "https://ado/pr/1",
              // echoes the resolved interval so the test can assert what the engine was handed
              "period=" + period.start() + ".." + period.end(),
              nodeId,
              Instant.parse("2026-06-10T10:00:00Z"),
              1.0,
              true,
              null));
    }
  }
}
