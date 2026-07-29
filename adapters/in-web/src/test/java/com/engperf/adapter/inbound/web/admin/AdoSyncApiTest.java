package com.engperf.adapter.inbound.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.application.ado.AdoStats;
import com.engperf.application.ado.DeviceCodePrompt;
import com.engperf.application.ado.SyncStatus;
import com.engperf.application.port.inbound.AdoStatsUseCase;
import com.engperf.application.port.inbound.AdoSyncUseCase;
import com.engperf.domain.metrics.EventType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Sync API: start returns the device-code prompt; status returns progress. Admin-only is enforced
 * by the {@code /api/admin/**} gate in the auth filter (not this controller).
 */
class AdoSyncApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new AdoSyncController(new FakeSync(), new FakeStats()))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();
  }

  @Test
  void startReturnsTheDeviceCodePrompt() throws Exception {
    mvc.perform(post("/api/admin/ado/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("s1"))
        .andExpect(jsonPath("$.userCode").value("ABC-123"))
        .andExpect(jsonPath("$.verificationUri").value("https://microsoft.com/devicelogin"));
  }

  @Test
  void statusReturnsProgress() throws Exception {
    mvc.perform(get("/api/admin/ado/sync/status?sessionId=s1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("syncing"))
        .andExpect(jsonPath("$.counts.commits").value(12));
  }

  @Test
  void unknownSessionIs404() throws Exception {
    mvc.perform(get("/api/admin/ado/sync/status?sessionId=nope")).andExpect(status().isNotFound());
  }

  @Test
  void statsReturnsTotalsAndRowsForTheNode() throws Exception {
    mvc.perform(get("/api/admin/ado/stats?node=all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodeLabel").value("Toda a estrutura"))
        .andExpect(jsonPath("$.totals.total").value(3))
        .andExpect(jsonPath("$.totals.byType.COMMIT").value(2))
        .andExpect(jsonPath("$.rows[0].label").value("Pagamentos"));
  }

  private static final class FakeStats implements AdoStatsUseCase {
    @Override
    public AdoStats stats(String nodeId) {
      Map<EventType, Long> byType = Map.of(EventType.COMMIT, 2L, EventType.DEPLOY, 1L);
      return new AdoStats(
          Instant.parse("2026-07-01T00:00:00Z"),
          Instant.parse("2026-07-01T00:00:00Z"),
          3L,
          "all",
          "Toda a estrutura",
          "vertical",
          new AdoStats.Totals(
              3L,
              3L,
              0L,
              byType,
              Instant.parse("2026-01-01T00:00:00Z"),
              Instant.parse("2026-07-01T00:00:00Z")),
          List.of(new AdoStats.Row("v:pag", "Pagamentos", "vertical", 3L, byType)));
    }
  }

  private static final class FakeSync implements AdoSyncUseCase {
    @Override
    public Session start() {
      return new Session(
          "s1", new DeviceCodePrompt("ABC-123", "https://microsoft.com/devicelogin", "d", 5, 900));
    }

    @Override
    public Optional<SyncStatus> status(String sessionId) {
      if (!sessionId.equals("s1")) {
        return Optional.empty();
      }
      return Optional.of(
          new SyncStatus("s1", "syncing", Map.of("commits", 12), false, false, "", Instant.now()));
    }
  }
}
