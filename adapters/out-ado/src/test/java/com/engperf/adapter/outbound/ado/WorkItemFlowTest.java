package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * A reconstrução do flow depende das datas das revisões — e o Azure DevOps datas a revisão vigente
 * com o sentinela {@code 9999-01-01}, que significa "ainda não substituída", não "sem data".
 */
class WorkItemFlowTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
  private static final Instant CHANGED = Instant.parse("2026-09-25T16:32:00Z");

  /** Classificador simples: Closed/Done → DONE, Active → ACTIVE, o resto → WAITING. */
  private static final Function<String, Segment> CLASSIFY =
      state ->
          switch (state.toLowerCase(Locale.ROOT)) {
            case "closed", "done", "resolved" -> Segment.DONE;
            case "active" -> Segment.ACTIVE;
            default -> Segment.WAITING;
          };

  /**
   * O caso relatado em produção (WI 73079/73081): o item foi fechado e não tocado depois, então a
   * transição para Closed é a revisão vigente e vem com o sentinela. Descartá-la fazia o item nunca
   * contar como entregue — e o throughput subcontava todo item concluído de forma limpa.
   */
  @Test
  void theTerminalTransitionOnTheOpenRevisionIsDatedByChangedDate() {
    JsonNode updates =
        updates(
            state("2026-09-20T10:00:00Z", "New", "Active"),
            state("9999-01-01T00:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("73079", updates, CLASSIFY, null, CHANGED, NOW);

    assertThat(flow.completion()).isEqualTo(CHANGED);
    Map<String, String> detail = new HashMap<>();
    flow.fill(detail);
    assertThat(detail).containsEntry("completed", "1").doesNotContainKey("in_progress");
  }

  /**
   * Sem ChangedDate utilizável a transição é descartada, e sobra uma única transição — o que faz o
   * item não produzir flow nenhum. É o comportamento antigo para todo item fechado: nem
   * "concluído", nem "em andamento", simplesmente ausente das métricas de fluxo.
   */
  @Test
  void aDiscardedTerminalTransitionLeavesTheItemWithoutAnyFlow() {
    JsonNode updates =
        updates(
            state("2026-09-20T10:00:00Z", "New", "Active"),
            state("9999-01-01T00:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, null, NOW);

    assertThat(flow.completion()).isNull();
    assertThat(flow.started()).isFalse();
    Map<String, String> detail = new HashMap<>();
    flow.fill(detail);
    assertThat(detail).as("nem concluído, nem em andamento — sem dados").isEmpty();
  }

  /** Um ChangedDate no futuro seria pior que nenhum: dataria a transição fora da realidade. */
  @Test
  void aFutureChangedDateIsNotUsed() {
    JsonNode updates =
        updates(
            state("2026-09-20T10:00:00Z", "New", "Active"),
            state("9999-01-01T00:00:00Z", "Active", "Closed"));

    WorkItemFlow flow =
        WorkItemFlow.of("x", updates, CLASSIFY, null, Instant.parse("2027-01-01T00:00:00Z"), NOW);

    assertThat(flow.completion()).isNull();
  }

  /** Data ilegível é outro problema, sem relação com o sentinela — continua sendo descartada. */
  @Test
  void anUnparseableRevisedDateIsStillDiscarded() {
    JsonNode updates =
        updates(
            state("2026-09-20T10:00:00Z", "New", "Active"),
            state("not-a-date", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW);

    assertThat(flow.completion()).isNull();
  }

  /** Item já fechado e editado depois: a transição terminal tem data real e nada muda. */
  @Test
  void anAlreadySupersededTerminalTransitionKeepsItsOwnDate() {
    Instant closedAt = Instant.parse("2026-09-22T09:00:00Z");
    JsonNode updates =
        updates(
            state("2026-09-20T10:00:00Z", "New", "Active"),
            state(closedAt.toString(), "Active", "Closed"),
            // revisão vigente: uma edição de campo qualquer, sem mudança de estado
            json("{\"revisedDate\":\"9999-01-01T00:00:00Z\",\"fields\":{}}"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW);

    assertThat(flow.completion()).isEqualTo(closedAt);
  }

  @Test
  void activeTimeIsMeasuredUpToTheRecoveredCompletion() {
    JsonNode updates =
        updates(
            state("2026-09-25T10:32:00Z", "New", "Active"),
            state("9999-01-01T00:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW);

    assertThat(flow.activeH()).isEqualTo(6.0); // 10:32 → 16:32
    assertThat(flow.cycleH()).isEqualTo(6.0);
  }

  private static JsonNode state(String revisedDate, String from, String to) {
    return json(
        "{\"revisedDate\":\""
            + revisedDate
            + "\",\"fields\":{\"System.State\":{\"oldValue\":\""
            + from
            + "\",\"newValue\":\""
            + to
            + "\"}}}");
  }

  private static JsonNode updates(JsonNode... entries) {
    StringBuilder sb = new StringBuilder("{\"value\":[");
    for (int i = 0; i < entries.length; i++) {
      sb.append(i > 0 ? "," : "").append(entries[i]);
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
