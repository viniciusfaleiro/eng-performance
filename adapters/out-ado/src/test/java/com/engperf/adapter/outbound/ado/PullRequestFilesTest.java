package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * O PR Size é o diff do próprio pull request — o número que a aba <em>Files</em> mostra.
 *
 * <p>O que estes testes protegem é o erro que eles substituem: somar o {@code changeCounts} de cada
 * commit fazia um commit de merge arrastar milhares de arquivos da outra branch, e um arquivo
 * tocado em cinco commits contar cinco. Um PR de 5 arquivos era reportado como 2309.
 */
class PullRequestFilesTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASE = "https://dev.azure.com/org/Proj/_apis/git/repositories/repo";

  /**
   * O caso relatado: a última iteração tem 5 arquivos (mais uma pasta, que não conta), enquanto os
   * commits do PR — um deles de merge — somam milhares de itens que não são deste PR.
   */
  @Test
  void theCountIsThePullRequestsOwnDiff() {
    FakeClient client =
        new FakeClient()
            .on("/iterations?", "pr-iterations.json")
            .on("/changes?", "pr-changes.json");

    assertThat(new PullRequestFiles(client).count(BASE, 24145, "tok")).hasValue(5);
  }

  /** Entre várias iterações, vale a de maior id: as anteriores são histórico de pushes. */
  @Test
  void theLatestIterationIsTheOneRead() {
    FakeClient client =
        new FakeClient()
            .on("/iterations?", "pr-iterations.json")
            .on("/changes?", "pr-changes.json");

    new PullRequestFiles(client).count(BASE, 24145, "tok");

    assertThat(client.urls).anyMatch(u -> u.contains("/iterations/3/changes"));
    assertThat(client.urls).noneMatch(u -> u.contains("/iterations/1/changes"));
    assertThat(client.urls).allMatch(u -> !u.contains("/commits/"));
  }

  /** Diff longo: enquanto a resposta oferecer nextSkip, as páginas somam. */
  @Test
  void aPagedDiffIsCountedWhole() {
    FakeClient client =
        new FakeClient()
            .on("/iterations?", "pr-iterations.json")
            .json(
                "/changes?$compareTo=0&$top=2000&api-version=7.1",
                "{\"changeEntries\":[" + blobs(3) + "],\"nextSkip\":3}")
            .json("$skip=3", "{\"changeEntries\":[" + blobs(2) + "]}");

    assertThat(new PullRequestFiles(client).count(BASE, 1, "tok")).hasValue(5);
  }

  @Test
  void folderEntriesAreNotFiles() {
    FakeClient client =
        new FakeClient()
            .on("/iterations?", "pr-iterations.json")
            .json(
                "/changes?",
                "{\"changeEntries\":["
                    + "{\"item\":{\"path\":\"/a.cs\",\"gitObjectType\":\"blob\"}},"
                    + "{\"item\":{\"path\":\"/dir\",\"isFolder\":true}},"
                    + "{\"item\":{\"path\":\"/dir2\",\"gitObjectType\":\"tree\"}}]}");

    assertThat(new PullRequestFiles(client).count(BASE, 1, "tok")).hasValue(1);
  }

  /** Sem iteração não há diff — e sem dado continua sem dado, nunca um substituto. */
  @Test
  void aPullRequestWithoutIterationsHasNoCount() {
    FakeClient client = new FakeClient().json("/iterations?", "{\"count\":0,\"value\":[]}");

    assertThat(new PullRequestFiles(client).count(BASE, 1, "tok")).isEmpty();
  }

  @Test
  void aFailedReadLeavesThePullRequestWithoutACount() {
    FakeClient failing = new FakeClient().failOn("/iterations?");
    assertThat(new PullRequestFiles(failing).count(BASE, 1, "tok")).isEmpty();

    FakeClient failingChanges =
        new FakeClient().on("/iterations?", "pr-iterations.json").failOn("/changes?");
    assertThat(new PullRequestFiles(failingChanges).count(BASE, 1, "tok")).isEmpty();
  }

  /** O custo da leitura é um número que a sync reporta, não uma estimativa. */
  @Test
  void theRequestsAreCounted() {
    FakeClient client =
        new FakeClient()
            .on("/iterations?", "pr-iterations.json")
            .on("/changes?", "pr-changes.json");
    PullRequestFiles files = new PullRequestFiles(client);

    files.count(BASE, 1, "tok");
    files.count(BASE, 2, "tok");

    assertThat(files.requests()).as("iterações + mudanças, por PR").isEqualTo(4);
  }

  // ---- helpers ----

  private static String blobs(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(i > 0 ? "," : "")
          .append("{\"item\":{\"path\":\"/f")
          .append(i)
          .append(".cs\",\"gitObjectType\":\"blob\"}}");
    }
    return sb.toString();
  }

  /** Responde por trecho da URL, na ordem em que as regras foram declaradas. */
  private static final class FakeClient implements AdoRestClient {
    private final List<String[]> rules = new ArrayList<>();
    private final List<String> failing = new ArrayList<>();
    final List<String> urls = new ArrayList<>();

    FakeClient on(String match, String fixture) {
      return json(match, read(fixture));
    }

    FakeClient json(String match, String body) {
      rules.add(new String[] {match, body});
      return this;
    }

    FakeClient failOn(String match) {
      failing.add(match);
      return this;
    }

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      for (String f : failing) {
        if (url.contains(f)) {
          throw new IllegalStateException("502 do ADO");
        }
      }
      // A regra mais específica primeiro: a segunda página casa por "$skip=", e a primeira pela
      // URL inteira sem skip.
      for (String[] rule : rules) {
        if (url.contains(rule[0])) {
          return parse(rule[1]);
        }
      }
      throw new IllegalStateException("sem regra para " + url);
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      throw new UnsupportedOperationException();
    }
  }

  private static String read(String fixture) {
    try {
      return new String(
          PullRequestFilesTest.class.getResourceAsStream("/ado/" + fixture).readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode parse(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
