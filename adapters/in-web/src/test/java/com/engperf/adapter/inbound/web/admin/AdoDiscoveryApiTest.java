package com.engperf.adapter.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.application.ado.DeviceCodePrompt;
import com.engperf.application.ado.DiscoveredRepository;
import com.engperf.application.ado.RepositoryDiscoveryStatus;
import com.engperf.application.port.inbound.AdoDiscoveryUseCase;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Discovery API: start returns the device-code prompt; status returns the diff once ready; apply
 * mutates the registry (or rejects an unfinished/unknown session).
 */
class AdoDiscoveryApiTest {

  private MockMvc mvc;
  private FakeDiscovery discovery;

  @BeforeEach
  void setUp() {
    discovery = new FakeDiscovery();
    mvc =
        MockMvcBuilders.standaloneSetup(new AdoDiscoveryController(discovery))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();
  }

  @Test
  void startReturnsTheDeviceCodePrompt() throws Exception {
    mvc.perform(
            post("/api/admin/repositories/discover")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organization\":\"orgX\",\"project\":\"ProjP\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("s1"))
        .andExpect(jsonPath("$.userCode").value("ABC-123"));
  }

  @Test
  void statusReturnsTheDiff() throws Exception {
    mvc.perform(get("/api/admin/repositories/discover/status?sessionId=s1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.done").value(true))
        .andExpect(jsonPath("$.toInsert[0].key").value("repoB"))
        .andExpect(jsonPath("$.toInsert[0].suggestedProductionStage").value("Production"))
        .andExpect(jsonPath("$.toRemove[0]").value("repoC"));
  }

  @Test
  void unknownSessionStatusIs404() throws Exception {
    mvc.perform(get("/api/admin/repositories/discover/status?sessionId=nope"))
        .andExpect(status().isNotFound());
  }

  @Test
  void applyAppliesTheDiff() throws Exception {
    mvc.perform(post("/api/admin/repositories/discover/apply?sessionId=s1"))
        .andExpect(status().isNoContent());
    assertThat(discovery.applied).isTrue();
  }

  @Test
  void applyOnUnknownSessionIs404() throws Exception {
    mvc.perform(post("/api/admin/repositories/discover/apply?sessionId=nope"))
        .andExpect(status().isNotFound());
  }

  @Test
  void applyOnUnfinishedSessionIs422() throws Exception {
    mvc.perform(post("/api/admin/repositories/discover/apply?sessionId=running"))
        .andExpect(status().isUnprocessableEntity());
  }

  private static final class FakeDiscovery implements AdoDiscoveryUseCase {
    boolean applied;

    @Override
    public Session start(String organization, String project) {
      return new Session(
          "s1", new DeviceCodePrompt("ABC-123", "https://microsoft.com/devicelogin", "d", 5, 900));
    }

    @Override
    public Optional<RepositoryDiscoveryStatus> status(String sessionId) {
      if (sessionId.equals("s1")) {
        return Optional.of(
            new RepositoryDiscoveryStatus(
                "s1",
                "done",
                true,
                false,
                "1 a inserir, 1 a remover",
                List.of(new DiscoveredRepository("repoB", "Production")),
                List.of("repoC"),
                false));
      }
      if (sessionId.equals("running")) {
        return Optional.of(
            new RepositoryDiscoveryStatus(
                "running", "listing", false, false, "", List.of(), List.of(), false));
      }
      return Optional.empty();
    }

    @Override
    public void apply(String sessionId) {
      if (sessionId.equals("nope") || sessionId.equals("unknown")) {
        throw new NoSuchElementException("unknown discovery session: " + sessionId);
      }
      if (!sessionId.equals("s1")) {
        throw new IllegalStateException("discovery session not finished: " + sessionId);
      }
      applied = true;
    }
  }
}
