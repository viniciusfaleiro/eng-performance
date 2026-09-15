package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A truncated commit message hides the AI trailers, so it has to be reloaded in full. */
class CommitCommentsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASE = "https://dev.azure.com/org/Proj/_apis/git/repositories/repo";

  @Test
  void anUntruncatedCommitIsReturnedAsIsWithoutAnExtraCall() {
    RecordingClient client = new RecordingClient("{}");

    JsonNode result = CommitComments.full(client, BASE, fixture("commit.json"), "tok");

    assertThat(client.urls).isEmpty(); // the common case must stay free
    assertThat(result.path("commitId").asText()).isEqualTo("abc123");
  }

  @Test
  void aTruncatedCommitIsReloadedSoTheTrailerBecomesVisible() {
    RecordingClient client =
        new RecordingClient(
            "{\"commitId\":\"def456\",\"comment\":\"feat: nova régua de limite\\n\\nCorpo"
                + " longo\\n\\nCo-authored-by: Copilot <copilot@github.com>\"}");

    JsonNode result = CommitComments.full(client, BASE, fixture("commit-truncated.json"), "tok");

    assertThat(client.urls).containsExactly(BASE + "/commits/def456?api-version=7.1");
    assertThat(result.path("comment").asText()).contains("Co-authored-by: Copilot");
  }

  /**
   * The reload is an optimisation of the AI flag, not a load-bearing step: a months-long backfill
   * must not die because one commit could not be re-read.
   */
  @Test
  void aFailedReloadFallsBackToTheTruncatedCommit() {
    JsonNode truncated = fixture("commit-truncated.json");
    AdoRestClient failing =
        new AdoRestClient() {
          @Override
          public JsonNode get(String url, String token) {
            throw new IllegalStateException("Azure DevOps commits -> HTTP 500");
          }

          @Override
          public JsonNode post(String url, String token, String body) {
            throw new UnsupportedOperationException();
          }
        };

    JsonNode result = CommitComments.full(failing, BASE, truncated, "tok");

    assertThat(result).isSameAs(truncated);
  }

  private static final class RecordingClient implements AdoRestClient {
    private final String body;
    final List<String> urls = new ArrayList<>();

    RecordingClient(String body) {
      this.body = body;
    }

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      return json(body);
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      throw new UnsupportedOperationException();
    }
  }

  private static JsonNode fixture(String name) {
    try {
      return JSON.readTree(CommitCommentsTest.class.getResourceAsStream("/ado/" + name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
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
