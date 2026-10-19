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
 * O trecho em que o item esteve comprometido e não entregue — o que o WIP passa a contar.
 *
 * <p>Ele cobre fila e trabalho igualmente, do momento em que o time pegou o item até a entrega,
 * menos o tempo impedido. O que estes testes mais protegem é o que <em>não</em> muda: as horas de
 * Espera, Ativo e Review de qualquer item são idênticas às de antes de o trecho existir.
 */
class WorkItemCommittedStretchTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
  private static final ImpedimentConvention OFF = ImpedimentConvention.off();
  private static final ImpedimentConvention SIGNAL =
      new ImpedimentConvention("Impediment", "Impedimento:");

  /** O board de User Story / Bug / Tech Debt, estado por estado. */
  private static final Function<String, Segment> BOARD =
      state ->
          switch (state.toLowerCase(Locale.ROOT)) {
            case "refinement", "new" -> Segment.WAITING;
            case "ready", "pronto p/ prod" -> Segment.QUEUE;
            case "code review" -> Segment.REVIEW;
            case "done", "closed", "resolved" -> Segment.DONE;
            default -> Segment.ACTIVE;
          };

  @Test
  void theStretchRunsFromTheCommittedQueueToTheTerminalState() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "Refinement"),
            state("2026-09-21T00:00:00Z", "Ready"),
            state("2026-09-22T00:00:00Z", "Doing"),
            state("2026-09-23T00:00:00Z", "Code Review"),
            state("2026-09-24T00:00:00Z", "Pronto p/ Prod"),
            state("2026-09-26T00:00:00Z", "Done"));

    assertThat(committed(updates, OFF))
        .isEqualTo(span("2026-09-21T00:00:00Z", "2026-09-26T00:00:00Z"));
  }

  /** Na Task não existe fila: o compromisso começa no próprio trabalho. */
  @Test
  void aTaskIsCommittedFromItsFirstWorkingState() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "New"),
            state("2026-09-21T00:00:00Z", "Active"),
            state("2026-09-22T00:00:00Z", "Done"));

    assertThat(committed(updates, OFF))
        .isEqualTo(span("2026-09-21T00:00:00Z", "2026-09-22T00:00:00Z"));
  }

  @Test
  void anItemThatNeverLeftTheBacklogHasNoCommittedStretch() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "Refinement"), state("2026-09-25T00:00:00Z", "Closed"));

    Map<String, String> detail = detail(updates, OFF);

    assertThat(detail).doesNotContainKey("committed_spans");
  }

  /** Item ainda aberto: a ponta fica no relógio da ingestão e o leitor a estende. */
  @Test
  void anOpenItemKeepsTheStretchOpenEnded() {
    JsonNode updates =
        updates(state("2026-09-20T00:00:00Z", "Ready"), state("2026-09-21T00:00:00Z", "Doing"));

    Map<String, String> detail = detail(updates, OFF);

    assertThat(detail.get("committed_spans"))
        .isEqualTo(span("2026-09-20T00:00:00Z", NOW.toString()));
    assertThat(detail).containsEntry("in_progress", "1");
  }

  @Test
  void impededTimeIsCutOutOfTheStretch() {
    JsonNode updates =
        updates(
            state("2026-09-21T00:00:00Z", "Ready"),
            state("2026-09-22T00:00:00Z", "Doing"),
            tags("2026-09-23T00:00:00Z", "", "Impedimento:infra"),
            tags("2026-09-24T00:00:00Z", "Impedimento:infra", ""),
            state("2026-09-25T00:00:00Z", "Done"));

    assertThat(committed(updates, SIGNAL))
        .isEqualTo(
            span("2026-09-21T00:00:00Z", "2026-09-23T00:00:00Z")
                + ","
                + span("2026-09-24T00:00:00Z", "2026-09-25T00:00:00Z"));
  }

  /** Item devolvido ao refinamento no meio continua comprometido — ele segue devido ao time. */
  @Test
  void anItemSentBackToRefinementStaysCommitted() {
    JsonNode updates =
        updates(
            state("2026-09-21T00:00:00Z", "Ready"),
            state("2026-09-22T00:00:00Z", "Doing"),
            state("2026-09-23T00:00:00Z", "Refinement"),
            state("2026-09-24T00:00:00Z", "Doing"),
            state("2026-09-25T00:00:00Z", "Done"));

    assertThat(committed(updates, OFF))
        .isEqualTo(span("2026-09-21T00:00:00Z", "2026-09-25T00:00:00Z"));
  }

  /**
   * A invariante que protege a quebra do cycle time: a fila comprometida é espera como qualquer
   * outra, então nenhuma hora muda de fase por causa desta mudança.
   */
  @Test
  void thePhaseHoursAreUntouchedByTheCommittedStretch() {
    JsonNode updates =
        updates(
            state("2026-09-20T00:00:00Z", "Refinement"),
            state("2026-09-21T00:00:00Z", "Ready"),
            state("2026-09-22T00:00:00Z", "Doing"),
            state("2026-09-23T00:00:00Z", "Code Review"),
            state("2026-09-24T00:00:00Z", "Pronto p/ Prod"),
            state("2026-09-26T00:00:00Z", "Done"));

    Map<String, String> detail = detail(updates, OFF);

    // Espera = 24h em Pronto p/ Prod + 24h? Não: o backlog antes do trabalho é ignorado, e a fila
    // Ready (antes do primeiro Doing) também — exatamente como era antes de QUEUE existir.
    assertThat(detail).containsEntry("active_h", "24.0"); // Doing 22→23
    assertThat(detail).containsEntry("review_h", "24.0"); // Code Review 23→24
    assertThat(detail).containsEntry("wait_h", "48.0"); // Pronto p/ Prod 24→26
    assertThat(detail).containsEntry("cycle_h", "96.0"); // primeiro trabalho → Done
  }

  // ---- helpers ----

  private static String committed(JsonNode updates, ImpedimentConvention c) {
    return detail(updates, c).get("committed_spans");
  }

  private static Map<String, String> detail(JsonNode updates, ImpedimentConvention c) {
    Map<String, String> detail = new HashMap<>();
    WorkItemFlow.of("x", updates, BOARD, null, Instant.parse("2026-09-26T00:00:00Z"), NOW, c)
        .fill(detail);
    return detail;
  }

  private static String span(String from, String to) {
    return Instant.parse(from).toEpochMilli() + ":" + Instant.parse(to).toEpochMilli();
  }

  private static JsonNode state(String revisedDate, String to) {
    return json(
        "{\"revisedDate\":\""
            + revisedDate
            + "\",\"fields\":{\"System.State\":{\"newValue\":\""
            + to
            + "\"}}}");
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
