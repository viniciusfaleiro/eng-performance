package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.engperf.application.ado.DiscoveredRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lists a project's repositories and guesses each one's production stage from recent builds —
 * exactly one matching stage name sets the suggestion, zero or more than one leaves it null.
 */
class AdoRepositoryDiscoveryTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void listsRepositoriesAndSuggestsAStageOnlyWhenExactlyOneCandidateIsFound() {
    FakeClient client = new FakeClient();
    AdoRepositoryDiscovery discovery = new AdoRepositoryDiscovery(client);

    List<DiscoveredRepository> found = discovery.discover("tok", "minhaorg", "Proj");

    assertThat(found)
        .extracting(DiscoveredRepository::key, DiscoveredRepository::suggestedProductionStage)
        .containsExactly(
            tuple("repoA", "Production"), // one consistent candidate
            tuple("repoB", null), // two different candidates → ambiguous
            tuple("repoC", null)); // no stage matches the heuristic

    assertThat(client.urls)
        .anyMatch(u -> u.contains("dev.azure.com/minhaorg/Proj/_apis/git/repositories?"));
  }

  @Test
  void toleratesNoBuildHistory() {
    FakeClient client =
        new FakeClient() {
          @Override
          public JsonNode get(String url, String token) {
            if (url.contains("_apis/git/repositories?")) {
              return json("{\"value\":[{\"name\":\"lonely-repo\"}]}");
            }
            throw new RuntimeException("no pipelines in this project");
          }
        };
    AdoRepositoryDiscovery discovery = new AdoRepositoryDiscovery(client);

    List<DiscoveredRepository> found = discovery.discover("tok", "org", "Proj");

    assertThat(found).containsExactly(new DiscoveredRepository("lonely-repo", null));
  }

  private static class FakeClient implements AdoRestClient {
    final List<String> urls = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      if (url.contains("_apis/git/repositories?")) {
        return json("{\"value\":[{\"name\":\"repoA\"},{\"name\":\"repoB\"},{\"name\":\"repoC\"}]}");
      }
      if (url.contains("_apis/build/builds?")) {
        return json(
            "{\"value\":["
                + "{\"id\":1,\"repository\":{\"name\":\"repoA\"}},"
                + "{\"id\":2,\"repository\":{\"name\":\"repoB\"}},"
                + "{\"id\":3,\"repository\":{\"name\":\"repoB\"}},"
                + "{\"id\":4,\"repository\":{\"name\":\"repoC\"}}]}");
      }
      if (url.contains("/builds/1/timeline")) {
        return timeline("Production");
      }
      if (url.contains("/builds/2/timeline")) {
        return timeline("Prod-US");
      }
      if (url.contains("/builds/3/timeline")) {
        return timeline("Prod-EU"); // different name than build 2 → repoB becomes ambiguous
      }
      if (url.contains("/builds/4/timeline")) {
        return timeline("Staging"); // no "prod"/"prd" substring → repoC has no candidate
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      urls.add(url);
      return json("{}");
    }

    static JsonNode timeline(String stageName) {
      return json(
          "{\"records\":[{\"id\":\"s\",\"type\":\"Stage\",\"name\":\"" + stageName + "\"}]}");
    }
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
