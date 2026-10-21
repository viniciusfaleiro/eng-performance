package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * O endereço de um pull request: construído, não lido do payload.
 *
 * <p>A sync lê a <em>lista</em> de pull requests, e essa resposta não traz {@code _links}. Enquanto
 * o endereço vinha desse campo, todo PR saía sem link e nenhuma linha do drill-down abria — numa
 * lista que existe justamente para o time conferir o número item a item.
 */
class AdoPullRequestLinkTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final VoteHistory APPROVED = new VoteHistory(false, true);
  private static final String WEB = "https://dev.azure.com/org/Proj/_git/checkout-service";

  /**
   * A resposta da lista de pull requests não traz {@code _links} — é dela que a sync lê. Enquanto o
   * endereço vinha desse campo, todo PR saía sem link e nenhuma linha do drill-down abria.
   */
  @Test
  void aPullRequestWithoutLinksStillGetsAnAddress() {
    RawEvent e =
        AdoMapper.pullRequest(
            fixture("pr-sem-links.json"),
            json("{\"value\":[]}"),
            false,
            APPROVED,
            OptionalInt.of(3),
            Set.of(),
            WEB);

    assertThat(e.detail().get("url"))
        .isEqualTo("https://dev.azure.com/org/Proj/_git/checkout-service/pullrequest/4711");
  }

  /** A review não tem registro próprio: ela reusa exatamente o endereço do pull request. */
  @Test
  void theReviewsOfThatPullRequestReuseItsAddress() {
    List<RawEvent> reviews =
        AdoMapper.reviews(fixture("pr-sem-links.json"), APPROVED, Set.of(), WEB);

    assertThat(reviews).isNotEmpty();
    assertThat(reviews)
        .allSatisfy(
            r ->
                assertThat(r.detail().get("url"))
                    .isEqualTo(
                        "https://dev.azure.com/org/Proj/_git/checkout-service/pullrequest/4711"));
  }

  /** Nome de repositório com espaço: o caminho encodado continua sendo um link válido. */
  @Test
  void aRepositoryNameWithSpacesKeepsTheLinkValid() {
    RawEvent e =
        AdoMapper.pullRequest(
            fixture("pr-sem-links.json"),
            json("{\"value\":[]}"),
            false,
            APPROVED,
            OptionalInt.of(1),
            Set.of(),
            "https://dev.azure.com/org/Core%20Cards/_git/core%20service");

    assertThat(e.detail().get("url"))
        .isEqualTo("https://dev.azure.com/org/Core%20Cards/_git/core%20service/pullrequest/4711");
  }

  private static JsonNode fixture(String name) {
    try {
      return JSON.readTree(AdoPullRequestLinkTest.class.getResourceAsStream("/ado/" + name));
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
