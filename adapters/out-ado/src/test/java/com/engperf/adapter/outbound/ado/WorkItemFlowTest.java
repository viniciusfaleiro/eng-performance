package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.config.ImpedimentConvention;
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
  private static final ImpedimentConvention OFF = ImpedimentConvention.off();

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

    WorkItemFlow flow = WorkItemFlow.of("73079", updates, CLASSIFY, null, CHANGED, NOW, OFF);

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

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, null, NOW, OFF);

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
        WorkItemFlow.of(
            "x", updates, CLASSIFY, null, Instant.parse("2027-01-01T00:00:00Z"), NOW, OFF);

    assertThat(flow.completion()).isNull();
  }

  /** Data ilegível é outro problema, sem relação com o sentinela — continua sendo descartada. */
  @Test
  void anUnparseableRevisedDateIsStillDiscarded() {
    JsonNode updates =
        updates(
            state("2026-09-20T10:00:00Z", "New", "Active"),
            state("not-a-date", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, OFF);

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

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, OFF);

    assertThat(flow.completion()).isEqualTo(closedAt);
  }

  @Test
  void activeTimeIsMeasuredUpToTheRecoveredCompletion() {
    JsonNode updates =
        updates(
            state("2026-09-25T10:32:00Z", "New", "Active"),
            state("9999-01-01T00:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, OFF);

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

  // ---- overlay de impedimento ----

  private static final ImpedimentConvention SIGNAL =
      new ImpedimentConvention("Impediment", "Impedimento:");

  /**
   * O caso que motivou a mudança: o item fica na coluna "Active" o tempo todo, mas parte dele está
   * travado. Sem o overlay, isso é lido como 100% trabalho e o Flow Efficiency sai inflado.
   */
  @Test
  void impedimentInsideAnActiveStateMovesHoursToWaiting() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            tags("2026-09-20T02:00:00Z", "", "Impedimento:infra"),
            tags("2026-09-20T06:00:00Z", "Impedimento:infra", ""),
            state("2026-09-20T10:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);

    assertThat(flow.activeH()).isEqualTo(6.0); // 10h na coluna, 4h delas travadas
    assertThat(flow.waitH()).isEqualTo(4.0);
    assertThat(flow.cycleH()).isEqualTo(10.0);
    assertThat(flow.impededByReason()).containsExactly(Map.entry("infra", 4.0));
  }

  /** O overlay só move horas: a soma das três fases é a mesma com e sem sinal. */
  @Test
  void theOverlayPreservesTheSumOfThePhases() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            tags("2026-09-20T02:00:00Z", "", "Impedimento:infra"),
            tags("2026-09-20T06:00:00Z", "Impedimento:infra", ""),
            state("2026-09-20T10:00:00Z", "Active", "Closed"));

    WorkItemFlow with = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);
    WorkItemFlow without = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, OFF);

    assertThat(with.activeH() + with.reviewH() + with.waitH())
        .isEqualTo(without.activeH() + without.reviewH() + without.waitH());
    assertThat(without.waitH()).isZero();
  }

  @Test
  void anEntirelyImpededReviewStateReportsNoReviewTime() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            state("2026-09-20T04:00:00Z", "Active", "Code Review"),
            tags("2026-09-20T04:00:00Z", "", "Impedimento:aprovação"),
            tags("2026-09-20T07:00:00Z", "Impedimento:aprovação", ""),
            state("2026-09-20T07:00:00Z", "Code Review", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);

    assertThat(flow.reviewH()).isZero();
    assertThat(flow.activeH()).isEqualTo(4.0);
    assertThat(flow.waitH()).isEqualTo(3.0);
    assertThat(flow.impededByReason()).containsExactly(Map.entry("aprovação", 3.0));
  }

  /**
   * Impedimento atravessando a fronteira: a metade que cai no estado de trabalho vira espera, e a
   * metade que já era espera por estado não é contada duas vezes — mas ganha motivo na quebra.
   */
  @Test
  void impedimentAcrossTheBorderIsNeverCountedTwice() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            tags("2026-09-20T02:00:00Z", "", "Impedimento:infra"),
            state("2026-09-20T04:00:00Z", "Active", "Blocked"),
            tags("2026-09-20T06:00:00Z", "Impedimento:infra", ""),
            state("2026-09-20T08:00:00Z", "Blocked", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);

    assertThat(flow.activeH()).isEqualTo(2.0); // 00→02 trabalhadas
    assertThat(flow.waitH()).isEqualTo(6.0); // 02→04 impedido + 04→08 em Blocked
    assertThat(flow.impededByReason()).containsExactly(Map.entry("infra", 4.0)); // 02→06
    assertThat(flow.activeH() + flow.reviewH() + flow.waitH()).isEqualTo(flow.cycleH());
  }

  /** Impedimento dentro de um estado que já era espera: a quebra o nomeia, o total não muda. */
  @Test
  void impedimentInsideAWaitingStateOnlyNamesTheReason() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            state("2026-09-20T02:00:00Z", "Active", "Blocked"),
            tags("2026-09-20T03:00:00Z", "", "Impedimento:infra"),
            tags("2026-09-20T05:00:00Z", "Impedimento:infra", ""),
            state("2026-09-20T06:00:00Z", "Blocked", "Closed"));

    WorkItemFlow signal = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);
    WorkItemFlow off = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, OFF);

    assertThat(signal.waitH()).isEqualTo(off.waitH()).isEqualTo(4.0);
    assertThat(signal.activeH()).isEqualTo(off.activeH()).isEqualTo(2.0);
    assertThat(signal.impededByReason()).containsExactly(Map.entry("infra", 2.0));
  }

  /** A invariante que mantém a quebra fechando com o card: motivos nunca excedem a Espera. */
  @Test
  void theReasonsNeverExceedTheWaitingHours() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            both("2026-09-20T01:00:00Z", "", "Yes", "", "Impedimento:infra"),
            both("2026-09-20T09:00:00Z", "Yes", "None", "Impedimento:infra", ""),
            state("2026-09-20T10:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);

    double reasons =
        flow.impededByReason().values().stream().mapToDouble(Double::doubleValue).sum();
    assertThat(reasons).isLessThanOrEqualTo(flow.waitH()).isEqualTo(8.0);
  }

  /** O tempo impedido no backlog, antes do primeiro trabalho, não entra em fase nenhuma. */
  @Test
  void impedimentBeforeTheFirstWorkIsIgnoredLikeTheBacklog() {
    JsonNode updates =
        updates(
            tags("2026-09-19T00:00:00Z", "", "Impedimento:infra"),
            tags("2026-09-19T10:00:00Z", "Impedimento:infra", ""),
            state("2026-09-20T00:00:00Z", "New", "Active"),
            state("2026-09-20T04:00:00Z", "Active", "Closed"));

    WorkItemFlow flow = WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL);

    assertThat(flow.activeH()).isEqualTo(4.0);
    assertThat(flow.waitH()).isZero();
    assertThat(flow.impededByReason()).isEmpty();
  }

  @Test
  void theDetailCarriesTheReasonsItsLabelAndTheImpededSpans() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            tags("2026-09-20T02:00:00Z", "", "Impedimento:Infra de CI"),
            tags("2026-09-20T06:00:00Z", "Impedimento:Infra de CI", ""),
            state("2026-09-20T10:00:00Z", "Active", "Closed"));

    Map<String, String> detail = new HashMap<>();
    WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, SIGNAL).fill(detail);

    assertThat(detail)
        .containsEntry("wait_h", "4.0")
        .containsEntry("active_h", "6.0")
        .containsEntry("impeded:infra de ci", "4.0")
        .containsEntry("impeded_label:infra de ci", "Infra de CI")
        .containsEntry(
            "impeded_spans",
            Instant.parse("2026-09-20T02:00:00Z").toEpochMilli()
                + ":"
                + Instant.parse("2026-09-20T06:00:00Z").toEpochMilli());
    assertThat(detail).doesNotContainKey("impeded_h");
  }

  /** Convenção vazia: o detail é byte a byte o de antes de o sinal existir. */
  @Test
  void anEmptyConventionChangesNothingInTheDetail() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New", "Active"),
            tags("2026-09-20T02:00:00Z", "", "Impedimento:infra"),
            tags("2026-09-20T06:00:00Z", "Impedimento:infra", ""),
            state("2026-09-20T10:00:00Z", "Active", "Closed"));

    Map<String, String> detail = new HashMap<>();
    WorkItemFlow.of("x", updates, CLASSIFY, null, CHANGED, NOW, OFF).fill(detail);

    assertThat(detail).containsEntry("active_h", "10.0").containsEntry("wait_h", "0.0");
    assertThat(detail.keySet()).noneMatch(k -> k.startsWith("impeded"));
  }

  private static JsonNode tags(String revisedDate, String from, String to) {
    return json(
        "{\"revisedDate\":\""
            + revisedDate
            + "\",\"fields\":{\"System.Tags\":{\"oldValue\":\""
            + from
            + "\",\"newValue\":\""
            + to
            + "\"}}}");
  }

  private static JsonNode both(
      String revisedDate, String fieldFrom, String fieldTo, String tagsFrom, String tagsTo) {
    return json(
        "{\"revisedDate\":\""
            + revisedDate
            + "\",\"fields\":{\"Impediment\":{\"oldValue\":\""
            + fieldFrom
            + "\",\"newValue\":\""
            + fieldTo
            + "\"},\"System.Tags\":{\"oldValue\":\""
            + tagsFrom
            + "\",\"newValue\":\""
            + tagsTo
            + "\"}}}");
  }
}
