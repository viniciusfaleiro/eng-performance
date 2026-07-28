package com.engperf.adapter.inbound.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.domain.structure.Repository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Repositories API: register one by one (org + team) and delete. */
class RepositoryApiTest {

  private final FakeRepositories repos = new FakeRepositories();
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new RepositoryController(repos)).build();
  }

  @Test
  void registerCreatesARepositoryWithItsOrgAndTeam() throws Exception {
    mvc.perform(
            post("/api/admin/repositories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"key\":\"checkout\",\"organization\":\"orgX\",\"project\":\"Pay\","
                        + "\"teamId\":\"t:checkout\",\"productionStage\":\"Production\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.key").value("checkout"))
        .andExpect(jsonPath("$.organization").value("orgX"))
        .andExpect(jsonPath("$.teamId").value("t:checkout"));
    org.assertj.core.api.Assertions.assertThat(repos.stored).hasSize(1);
  }

  @Test
  void deleteRemovesTheRepository() throws Exception {
    repos.stored.add(new Repository("checkout", "orgX", "Pay", "t:checkout", "Production"));
    mvc.perform(delete("/api/admin/repositories/checkout")).andExpect(status().isNoContent());
    org.assertj.core.api.Assertions.assertThat(repos.stored).isEmpty();
  }

  private static final class FakeRepositories implements RepositoryUseCase {
    final List<Repository> stored = new ArrayList<>();

    @Override
    public List<Repository> repositories() {
      return stored;
    }

    @Override
    public Repository register(
        String key, String organization, String project, String teamId, String productionStage) {
      Repository r = new Repository(key, organization, project, teamId, productionStage);
      stored.add(r);
      return r;
    }

    @Override
    public Repository mapToTeam(String repositoryKey, String teamId) {
      return stored.stream()
          .filter(r -> r.key().equals(repositoryKey))
          .findFirst()
          .map(r -> r.assignTo(teamId))
          .orElseThrow();
    }

    @Override
    public void delete(String repositoryKey) {
      stored.removeIf(r -> r.key().equals(repositoryKey));
    }
  }
}
