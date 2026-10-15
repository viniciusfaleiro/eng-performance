package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The link between a pull request and the board: what the sync stamps on the pull request, on its
 * commits and on its reviews, and what it does when there is no link to read.
 */
class AdoWorkTypeLinkTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName("o PR carrega as categorias dos work items vinculados, e o commit e o review também")
  void prCommitsAndReviewsCarryTheLinkedCategories() {
    LinkingClient client = new LinkingClient();
    List<RawEvent> events = sync(client);

    RawEvent pr = one(events, EventType.PR);
    assertThat(pr.detail().get("work_types")).isEqualTo("feature,bug");

    RawEvent commit = one(events, EventType.COMMIT);
    assertThat(commit.detail().get("work_types")).isEqualTo("feature,bug");

    RawEvent review = one(events, EventType.REVIEW);
    assertThat(review.detail().get("work_types")).isEqualTo("feature,bug");
  }

  @Test
  @DisplayName("PR sem vínculo fica sem tipo nenhum — nada de categoria padrão")
  void anUnlinkedPullRequestCarriesNoType() {
    UnlinkedClient client = new UnlinkedClient();
    List<RawEvent> events = sync(client);

    assertThat(one(events, EventType.PR).detail()).doesNotContainKey("work_types");
    assertThat(one(events, EventType.COMMIT).detail()).doesNotContainKey("work_types");
  }

  @Test
  @DisplayName("os tipos dos itens vinculados são resolvidos em lote, não um pedido por PR")
  void linkedItemTypesAreResolvedInBatches() {
    LinkingClient client = new LinkingClient();
    sync(client);

    List<String> batches =
        client.urls.stream().filter(u -> u.contains("wit/workitems?ids=")).toList();
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).contains("ids=11,22");
  }

  @Test
  @DisplayName("uma task vinculada ao PR herda a categoria do pai, como no board")
  void aLinkedTaskInheritsItsParentsCategory() {
    TaskLinkClient client = new TaskLinkClient();
    List<RawEvent> events = sync(client);

    assertThat(one(events, EventType.PR).detail().get("work_types")).isEqualTo("tech_debt");
  }

  /**
   * O backfill é o re-ingest completo que o Admin já aciona: o PR antigo, que foi gravado sem
   * vínculo, volta com os tipos sob o mesmo id — o store grava por id, então preencher a história
   * não duplica evento nenhum.
   */
  @Test
  @DisplayName("re-ingerir o mesmo PR preenche os tipos sob o mesmo id, sem duplicar")
  void reingestingFillsTheTypesUnderTheSameId() {
    RawEvent before = one(sync(new UnlinkedClient()), EventType.PR);
    List<RawEvent> after = sync(new LinkingClient());

    assertThat(before.detail()).doesNotContainKey("work_types");
    assertThat(after.stream().filter(e -> e.type() == EventType.PR).map(RawEvent::id))
        .containsExactly(before.id());
    assertThat(one(after, EventType.PR).detail().get("work_types")).isEqualTo("feature,bug");
  }

  private static List<RawEvent> sync(AdoRestClient client) {
    FakeStructure structure =
        new FakeStructure(List.of(new Repository("repoA", "orgX", "ProjP", "t:1", "Production")));
    return new AdoEventSource(client, new FakeConfig(), structure)
        .fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {})
        .events();
  }

  private static RawEvent one(List<RawEvent> events, EventType type) {
    return events.stream()
        .filter(e -> e.type() == type)
        .findFirst()
        .orElseThrow(() -> new AssertionError("nenhum evento do tipo " + type));
  }

  /** One pull request linked to a user story and a bug, with one commit and one approval. */
  private static class LinkingClient implements AdoRestClient {
    final List<String> urls = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      if (url.contains("/pullrequests?")) {
        return json(
            "{\"value\":[{\"pullRequestId\":42,\"title\":\"PR\","
                + "\"creationDate\":\"2026-06-10T09:00:00Z\","
                + "\"closedDate\":\"2026-06-10T15:00:00Z\","
                + "\"createdBy\":{\"uniqueName\":\"ana@empresa.com\"},"
                + "\"repository\":{\"name\":\"repoA\"},"
                + "\"reviewers\":[{\"uniqueName\":\"bruno@empresa.com\",\"vote\":10,"
                + "\"commentCount\":1}]}]}");
      }
      if (url.contains("/pullrequests/42/workitems")) {
        return json("{\"value\":[{\"id\":\"11\"},{\"id\":\"22\"}]}");
      }
      if (url.contains("/pullrequests/42/commits")) {
        return prCommits();
      }
      if (url.contains("wit/workitems?ids=")) {
        return linkedItemTypes();
      }
      if (url.contains("/commits?")) {
        return repoCommits();
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      urls.add(url);
      return json("{\"workItems\":[]}");
    }

    JsonNode linkedItemTypes() {
      return json(
          "{\"value\":[{\"id\":\"11\",\"fields\":{\"System.WorkItemType\":\"User Story\"}},"
              + "{\"id\":\"22\",\"fields\":{\"System.WorkItemType\":\"Bug\"}}]}");
    }

    static JsonNode prCommits() {
      return json(
          "{\"value\":[{\"commitId\":\"c1\",\"author\":{\"date\":\"2026-06-10T10:00:00Z\"}}]}");
    }

    static JsonNode repoCommits() {
      return json(
          "{\"value\":[{\"commitId\":\"c1\",\"comment\":\"fix: algo\","
              + "\"author\":{\"email\":\"ana@empresa.com\",\"date\":\"2026-06-10T10:00:00Z\"}}]}");
    }
  }

  /** The same pull request, linked to nothing. */
  private static final class UnlinkedClient extends LinkingClient {
    @Override
    public JsonNode get(String url, String token) {
      if (url.contains("/pullrequests/42/workitems")) {
        return json("{\"value\":[]}");
      }
      return super.get(url, token);
    }
  }

  /** The pull request is linked to a task whose parent is the team's debt item. */
  private static final class TaskLinkClient extends LinkingClient {
    @Override
    public JsonNode get(String url, String token) {
      if (url.contains("/pullrequests/42/workitems")) {
        return json("{\"value\":[{\"id\":\"11\"}]}");
      }
      if (url.contains("wit/workitems?ids=11&")) {
        return json(
            "{\"value\":[{\"id\":\"11\",\"fields\":{\"System.WorkItemType\":\"Task\","
                + "\"System.Parent\":\"99\"}}]}");
      }
      if (url.contains("wit/workitems?ids=99")) {
        return json(
            "{\"value\":[{\"id\":\"99\",\"fields\":{\"System.WorkItemType\":\"Tech Debt\"}}]}");
      }
      return super.get(url, token);
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
