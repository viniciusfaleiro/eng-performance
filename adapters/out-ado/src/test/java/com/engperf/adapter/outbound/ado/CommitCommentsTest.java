package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * A busca do commit por inteiro, que serve a dois propósitos: a mensagem truncada esconde os
 * trailers de IA, e a contagem de arquivos alterados só existe nessa resposta.
 */
class CommitCommentsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASE = "https://dev.azure.com/org/Proj/_apis/git/repositories/repo";
  private static final Predicate<String> IS_AI =
      msg -> msg.toLowerCase(Locale.ROOT).contains("co-authored-by: copilot");

  @Test
  void anUntruncatedCommitIsReturnedAsIsWithoutAnExtraCall() {
    RecordingClient client = new RecordingClient("{}");
    CommitComments comments = new CommitComments(client, IS_AI);

    JsonNode result = comments.full(BASE, fixture("commit.json"), "tok");

    assertThat(client.urls).isEmpty(); // the common case must stay free
    assertThat(comments.reloaded()).isZero();
    assertThat(result.path("commitId").asText()).isEqualTo("abc123");
  }

  @Test
  void aTruncatedCommitIsReloadedSoTheTrailerBecomesVisible() {
    RecordingClient client =
        new RecordingClient(
            "{\"commitId\":\"def456\",\"comment\":\"feat: nova régua de limite\\n\\nCorpo"
                + " longo\\n\\nCo-authored-by: Copilot <copilot@github.com>\"}");
    CommitComments comments = new CommitComments(client, IS_AI);

    JsonNode result = comments.full(BASE, fixture("commit-truncated.json"), "tok");

    assertThat(client.urls).containsExactly(BASE + "/commits/def456?changeCount=1&api-version=7.1");
    assertThat(comments.reloaded()).isEqualTo(1);
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

    CommitComments comments = new CommitComments(failing, IS_AI);

    JsonNode result = comments.full(BASE, truncated, "tok");

    assertThat(result).isSameAs(truncated);
    assertThat(comments.reloaded()).isZero(); // a failed call did not restore anything
  }

  /**
   * The marker can sit in the part that survived truncation — a tag in the subject line, say. The
   * reload could only confirm a flag that is already decided, so it is skipped.
   */
  @Test
  void aTruncatedCommitAlreadyMatchingTheConventionIsNotReloaded() {
    RecordingClient client = new RecordingClient("{}");
    CommitComments comments = new CommitComments(client, IS_AI);
    JsonNode visibleMatch =
        json(
            "{\"commitId\":\"ghi789\",\"commentTruncated\":true,"
                + "\"comment\":\"feat: régua\\n\\nCo-authored-by: Copilot <c@github.com>\\n\\ncorpo\"}");

    JsonNode result = comments.full(BASE, visibleMatch, "tok");

    assertThat(client.urls).isEmpty();
    assertThat(comments.reloaded()).isZero();
    assertThat(result).isSameAs(visibleMatch);
  }

  @Test
  void aPullRequestIsAiWhenAnyOfItsCommitsIs() {
    RecordingClient client = new RecordingClient("{}");
    CommitComments comments = new CommitComments(client, IS_AI);
    JsonNode prCommits =
        json(
            "{\"value\":["
                + "{\"commitId\":\"a\",\"comment\":\"fix: ajuste\"},"
                + "{\"commitId\":\"b\",\"comment\":\"feat: x\\n\\nCo-authored-by: Copilot <c@gh>\"}]}");

    assertThat(comments.anyAi(BASE, prCommits, "tok")).isTrue();
  }

  @Test
  void aPullRequestWithNoAiCommitIsNotAi() {
    RecordingClient client = new RecordingClient("{}");
    CommitComments comments = new CommitComments(client, IS_AI);
    JsonNode prCommits =
        json("{\"value\":[{\"commitId\":\"a\",\"comment\":\"fix: ajuste manual\"}]}");

    assertThat(comments.anyAi(BASE, prCommits, "tok")).isFalse();
    assertThat(client.urls).as("nada truncado, nenhuma chamada extra").isEmpty();
  }

  /**
   * O trailer fica no fim da mensagem, que é justamente o que o Azure DevOps corta. Sem recarregar,
   * o PR seria classificado como sem IA — o mesmo bug já corrigido para commits avulsos.
   */
  @Test
  void aPullRequestIsAiEvenWhenTheTrailerWasTruncatedAway() {
    RecordingClient client =
        new RecordingClient(
            "{\"commitId\":\"t\",\"comment\":\"feat: régua\\n\\ncorpo longo"
                + "\\n\\nCo-authored-by: Copilot <c@gh>\"}");
    CommitComments comments = new CommitComments(client, IS_AI);
    JsonNode prCommits =
        json(
            "{\"value\":[{\"commitId\":\"t\",\"commentTruncated\":true,"
                + "\"comment\":\"feat: régua\"}]}");

    assertThat(comments.anyAi(BASE, prCommits, "tok")).isTrue();
    assertThat(client.urls).containsExactly(BASE + "/commits/t?changeCount=1&api-version=7.1");
  }

  /**
   * O total do PR é a soma dos commits. Três commits de 2, 3 e 5 itens → 10 arquivos.
   *
   * <p>Soma itens, e não um trio fixo de tipos: a API relata também renomeação e mudança de
   * propriedade, e um arquivo renomeado é tão alterado quanto um editado. Ler só Add/Edit/Delete
   * subcontaria em silêncio — foi o que o mapper antigo fazia.
   */
  @Test
  void theFileCountIsSummedAcrossTheCommitsOfThePullRequest() {
    CountingClient client =
        new CountingClient(
            java.util.Map.of(
                "a", "{\"changeCounts\":{\"Add\":2}}",
                "b", "{\"changeCounts\":{\"Edit\":2,\"Rename\":1}}",
                "c", "{\"changeCounts\":{\"Add\":1,\"Edit\":3,\"Delete\":1}}"));
    CommitComments comments = new CommitComments(client, IS_AI);

    assertThat(comments.changedFiles(BASE, prCommits("a", "b", "c"), "tok")).hasValue(10);
    assertThat(client.urls).hasSize(3);
  }

  /**
   * Uma soma faltando um commit é indistinguível de uma medição legítima, e a mediana que a lê não
   * tem como saber que está baixa. Então o PR inteiro fica sem contagem.
   */
  @Test
  void oneCommitWithoutCountsLeavesThePullRequestWithoutATotal() {
    CountingClient client =
        new CountingClient(
            java.util.Map.of(
                "a", "{\"changeCounts\":{\"Add\":9}}",
                "b", "{\"commitId\":\"b\"}")); // resposta sem changeCounts

    assertThat(new CommitComments(client, IS_AI).changedFiles(BASE, prCommits("a", "b"), "tok"))
        .isEmpty();
  }

  /** Falha de rede não derruba o sync: o PR vai sem contagem. */
  @Test
  void aFailedRequestLeavesThePullRequestWithoutATotal() {
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

    assertThat(new CommitComments(failing, IS_AI).changedFiles(BASE, prCommits("a"), "tok"))
        .isEmpty();
  }

  /**
   * O commit que precisa da mensagem completa <em>e</em> da contagem é buscado uma vez. Sem a
   * memoização seriam duas chamadas para a mesma resposta, e o custo do sync dobraria sem nenhum
   * dado novo.
   */
  @Test
  void aCommitNeededForBothPurposesIsFetchedOnce() {
    CountingClient client =
        new CountingClient(
            java.util.Map.of(
                "t",
                "{\"commitId\":\"t\",\"comment\":\"feat: x\\n\\nCo-authored-by: Copilot\","
                    + "\"changeCounts\":{\"Edit\":4}}"));
    CommitComments comments = new CommitComments(client, IS_AI);
    JsonNode prCommits =
        json(
            "{\"value\":[{\"commitId\":\"t\",\"commentTruncated\":true,"
                + "\"comment\":\"feat: x\"}]}");

    assertThat(comments.anyAi(BASE, prCommits, "tok")).isTrue();
    assertThat(comments.changedFiles(BASE, prCommits, "tok")).hasValue(4);
    assertThat(client.urls).hasSize(1);
    assertThat(comments.reloaded()).isEqualTo(1);
  }

  private static JsonNode prCommits(String... ids) {
    StringBuilder sb = new StringBuilder("{\"value\":[");
    for (int i = 0; i < ids.length; i++) {
      sb.append(i == 0 ? "" : ",").append("{\"commitId\":\"").append(ids[i]).append("\"}");
    }
    return json(sb.append("]}").toString());
  }

  /** Responde por id de commit, para o teste poder dar contagens diferentes a cada um. */
  private static final class CountingClient implements AdoRestClient {
    private final java.util.Map<String, String> byId;
    final List<String> urls = new ArrayList<>();

    CountingClient(java.util.Map<String, String> byId) {
      this.byId = byId;
    }

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      String id = url.substring(url.lastIndexOf('/') + 1, url.indexOf('?'));
      return json(byId.getOrDefault(id, "{}"));
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      throw new UnsupportedOperationException();
    }
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
