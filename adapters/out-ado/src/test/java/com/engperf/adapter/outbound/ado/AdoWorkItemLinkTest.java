package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.config.ImpedimentConvention;
import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * O endereço de um work item: de que projeto ele sai.
 *
 * <p>A leitura em lote dos work items é da organização inteira — devolve qualquer id que receba, de
 * qualquer projeto, sem reclamar. Enquanto a URL era montada com o projeto que estava sendo
 * sincronizado, itens de outro projeto abriam sob o nome errado, e a lista do drill-down, que
 * existe para o time conferir se acredita no número, passava a parecer inventada.
 */
class AdoWorkItemLinkTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ImpedimentConvention OFF = ImpedimentConvention.off();

  private static final Function<String, Segment> CLASSIFY =
      name ->
          switch (name) {
            case "Closed", "Done", "Removed" -> Segment.DONE;
            case "New", "Blocked" -> Segment.WAITING;
            case "Code Review" -> Segment.REVIEW;
            default -> Segment.ACTIVE;
          };

  /**
   * O endereço segue o <em>item</em>, não o projeto que estava sendo sincronizado. A leitura em
   * lote é da organização inteira e devolve qualquer id que receba: carimbar esses itens com o
   * projeto da vez abria o item certo sob o nome errado, e era isso que derrubava a confiança na
   * lista do drill-down.
   */
  @Test
  void workItemLinkFollowsTheItemsOwnProject() {
    JsonNode wi =
        json(
            "{\"id\":77,\"fields\":{\"System.WorkItemType\":\"Bug\",\"System.Title\":\"t\","
                + "\"System.TeamProject\":\"Credenciadora\","
                + "\"System.CreatedDate\":\"2026-06-01T10:00:00Z\","
                + "\"System.ChangedDate\":\"2026-06-02T10:00:00Z\"}}");

    RawEvent e =
        AdoMapper.workItem(
            wi,
            updates(stateUpdate("2026-06-01T11:00:00Z", "Active")),
            CLASSIFY,
            Instant.parse("2026-06-30T12:00:00Z"),
            "https://dev.azure.com/org",
            ParentRef.none(),
            OFF);

    assertThat(e.detail().get("url"))
        .isEqualTo("https://dev.azure.com/org/Credenciadora/_workitems/edit/77");
    assertThat(e.detail().get("ado_project")).isEqualTo("Credenciadora");
  }

  /** Nome de projeto com espaço precisa de encode: sem ele o link passa de errado a quebrado. */
  @Test
  void aProjectNameWithSpacesIsPathEncoded() {
    JsonNode wi =
        json(
            "{\"id\":78,\"fields\":{\"System.WorkItemType\":\"Bug\",\"System.Title\":\"t\","
                + "\"System.TeamProject\":\"Core Cards\","
                + "\"System.CreatedDate\":\"2026-06-01T10:00:00Z\","
                + "\"System.ChangedDate\":\"2026-06-02T10:00:00Z\"}}");

    RawEvent e =
        AdoMapper.workItem(
            wi,
            updates(stateUpdate("2026-06-01T11:00:00Z", "Active")),
            CLASSIFY,
            Instant.parse("2026-06-30T12:00:00Z"),
            "https://dev.azure.com/org",
            ParentRef.none(),
            OFF);

    assertThat(e.detail().get("url"))
        .isEqualTo("https://dev.azure.com/org/Core%20Cards/_workitems/edit/78");
    assertThat(e.detail().get("ado_project")).isEqualTo("Core Cards");
  }

  private static JsonNode updates(String... entries) {
    return json("{\"value\":[" + String.join(",", entries) + "]}");
  }

  private static String stateUpdate(String date, String state) {
    return "{\"revisedDate\":\""
        + date
        + "\",\"fields\":{\"System.State\":{\"newValue\":\""
        + state
        + "\"}}}";
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
