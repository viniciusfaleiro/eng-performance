package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.structure.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Uma fonte inacessível custa só os dados dela. Antes, um repositório renomeado, removido ou fora
 * das permissões abortava a coleta inteira — cadastro desatualizado parava a plataforma de medir.
 */
class AdoEventSourceFailureTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Um repositório renomeado, removido ou sem permissão custava a coleta inteira: cadastro
   * desatualizado parava a plataforma de medir.
   */
  @Test
  void aRepositoryThatFailsDoesNotStopTheOthersAndIsReported() {
    FakeStructure structure =
        new FakeStructure(
            List.of(
                new Repository("quebrado", "orgX", "ProjP", "t:1", "Production"),
                new Repository("repoA", "orgX", "ProjP", "t:2", "Production")));
    AdoEventSource source = new AdoEventSource(new BrokenRepoClient(), new FakeConfig(), structure);

    var result =
        source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {});

    assertThat(result.isPartial()).isTrue();
    assertThat(result.failures())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.source()).isEqualTo("repositório orgX/ProjP/quebrado");
              assertThat(f.reason()).contains("TF401019");
            });
    // O repositório sadio foi coletado mesmo assim.
    assertThat(result.events())
        .as("os eventos do repo acessível entraram")
        .anySatisfy(e -> assertThat(e.detail().get("repo")).isEqualTo("repoA"));
  }

  @Test
  void aProjectThatFailsDoesNotStopTheRepositoryCollection() {
    FakeStructure structure =
        new FakeStructure(List.of(new Repository("repoA", "orgX", "ProjP", "t:1", "Production")));
    AdoEventSource source =
        new AdoEventSource(new BrokenProjectClient(), new FakeConfig(), structure);

    var result =
        source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {});

    assertThat(result.failures()).extracting(f -> f.source()).containsExactly("projeto orgX/ProjP");
    assertThat(result.events())
        .anySatisfy(e -> assertThat(e.detail().get("repo")).isEqualTo("repoA"));
  }

  /**
   * Ponta a ponta: o PR foi rejeitado e depois aprovado. O objeto do PR mostra só a aprovação — a
   * rejeição está nas threads, e é por isso que elas passaram a ser lidas.
   */
  @Test
  void aPullRequestRejectedThenApprovedIsIngestedAsNotFirstPass() {
    FakeStructure structure =
        new FakeStructure(List.of(new Repository("repoA", "orgX", "ProjP", "t:1", "Production")));
    ReopenedPrClient client = new ReopenedPrClient();
    AdoEventSource source = new AdoEventSource(client, new FakeConfig(), structure);

    var result =
        source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {});

    assertThat(client.urls).anyMatch(u -> u.contains("/pullrequests/7/threads"));
    assertThat(result.events())
        .filteredOn(e -> "pr:7".equals(e.id()))
        .singleElement()
        .satisfies(e -> assertThat(e.detail().get("first_pass")).isEqualTo("0"));
    // O revisor aprovou no fim, mas o review registra o pedido de mudança que houve.
    assertThat(result.events())
        .filteredOn(e -> e.id().startsWith("review:7"))
        .singleElement()
        .satisfies(e -> assertThat(e.detail().get("decision")).isEqualTo("changes_requested"));
  }

  /** PR com voto final +10 e uma rejeição no histórico de threads. */
  private static final class ReopenedPrClient implements AdoRestClient {
    final List<String> urls = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      if (url.contains("/threads")) {
        return json(
            "{\"value\":["
                + "{\"properties\":{\"CodeReviewThreadType\":{\"$value\":\"VoteUpdate\"},"
                + "\"CodeReviewVoteResult\":{\"$value\":\"-10\"}}},"
                + "{\"properties\":{\"CodeReviewThreadType\":{\"$value\":\"VoteUpdate\"},"
                + "\"CodeReviewVoteResult\":{\"$value\":\"10\"}}}]}");
      }
      if (url.contains("/pullrequests?")) {
        return json(
            "{\"value\":[{\"pullRequestId\":7,\"closedDate\":\"2026-06-10T15:00:00Z\","
                + "\"creationDate\":\"2026-06-10T09:00:00Z\",\"title\":\"fix\","
                + "\"createdBy\":{\"uniqueName\":\"ana@empresa.com\"},"
                + "\"repository\":{\"name\":\"repoA\"},"
                + "\"reviewers\":[{\"uniqueName\":\"bruno@empresa.com\",\"vote\":10}]}]}");
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      return json("{\"workItems\":[]}");
    }
  }

  private static final class BrokenRepoClient implements AdoRestClient {
    @Override
    public JsonNode get(String url, String token) {
      if (url.contains("/quebrado")) {
        throw new IllegalStateException(
            "Azure DevOps "
                + url
                + " -> HTTP 404 não encontrado — TF401019: The Git repository"
                + " with name or identifier quebrado does not exist");
      }
      if (url.contains("/commits?")) {
        return json(
            "{\"value\":[{\"commitId\":\"c1\",\"comment\":\"fix: ok\","
                + "\"author\":{\"email\":\"ana@empresa.com\",\"date\":\"2026-06-10T10:00:00Z\"}}]}");
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      return json("{\"workItems\":[]}");
    }
  }

  private static final class BrokenProjectClient implements AdoRestClient {
    @Override
    public JsonNode get(String url, String token) {
      if (url.contains("/build/builds")) {
        throw new IllegalStateException("Azure DevOps -> HTTP 403 sem permissão no projeto");
      }
      if (url.contains("/commits?")) {
        return json(
            "{\"value\":[{\"commitId\":\"c1\",\"comment\":\"fix: ok\","
                + "\"author\":{\"email\":\"ana@empresa.com\",\"date\":\"2026-06-10T10:00:00Z\"}}]}");
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      return json("{\"workItems\":[]}");
    }
  }

  private static JsonNode json(String s) {
    try {
      return JSON.readTree(s);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
