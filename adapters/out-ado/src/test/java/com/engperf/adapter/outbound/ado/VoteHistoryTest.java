package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * O voto atual do revisor apaga a própria história: rejeitar → autor corrige → aprovar deixa apenas
 * a aprovação no PR. Estes testes fixam a leitura das threads, que é onde a rejeição sobrevive.
 */
class VoteHistoryTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void aRejectionFollowedByAnApprovalIsNotFirstPass() {
    VoteHistory h = VoteHistory.of(threads(-10, 10));

    assertThat(h.changesRequested()).isTrue();
    assertThat(h.firstPass()).isFalse();
  }

  @Test
  void waitingForAuthorAlsoCountsAsChangesRequested() {
    assertThat(VoteHistory.of(threads(-5, 10)).firstPass()).isFalse();
  }

  @Test
  void approvedWithoutAnyNegativeVoteIsFirstPass() {
    VoteHistory h = VoteHistory.of(threads(10));

    assertThat(h.changesRequested()).isFalse();
    assertThat(h.firstPass()).isTrue();
  }

  /** Não saber se alguém pediu mudança não é prova de review limpo. */
  @Test
  void unknownHistoryIsNeverFirstPass() {
    assertThat(VoteHistory.UNKNOWN.firstPass()).isFalse();
    assertThat(VoteHistory.of(null).firstPass()).isFalse();
    assertThat(VoteHistory.of(json("{}")).known()).isFalse();
  }

  @Test
  void threadsThatAreNotVoteUpdatesAreIgnored() {
    JsonNode other =
        json(
            "{\"value\":[{\"properties\":{\"CodeReviewThreadType\":{\"$value\":\"StatusUpdate\"},"
                + "\"CodeReviewVoteResult\":{\"$value\":\"-10\"}}}]}");

    assertThat(VoteHistory.of(other).changesRequested()).isFalse();
    assertThat(VoteHistory.of(other).firstPass()).isTrue(); // leu o histórico: ninguém votou contra
  }

  /** O ADO devolve as propriedades embrulhadas em {@code $value}; escalares aparecem também. */
  @Test
  void bothPropertyShapesAreRead() {
    JsonNode plain =
        json(
            "{\"value\":[{\"properties\":{\"CodeReviewThreadType\":\"VoteUpdate\","
                + "\"CodeReviewVoteResult\":\"-10\"}}]}");

    assertThat(VoteHistory.of(plain).changesRequested()).isTrue();
  }

  private static JsonNode threads(int... votes) {
    StringBuilder sb = new StringBuilder("{\"value\":[");
    for (int i = 0; i < votes.length; i++) {
      sb.append(i > 0 ? "," : "")
          .append("{\"properties\":{\"CodeReviewThreadType\":{\"$value\":\"VoteUpdate\"},")
          .append("\"CodeReviewVoteResult\":{\"$value\":\"")
          .append(votes[i])
          .append("\"}}}");
    }
    return json(sb.append("]}").toString());
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
