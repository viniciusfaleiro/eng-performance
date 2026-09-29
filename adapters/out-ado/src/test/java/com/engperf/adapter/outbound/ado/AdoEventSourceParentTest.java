package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * O contrato HTTP da herança de tipo pela Task: quantas chamadas a mais ela custa, e o que acontece
 * quando o pai não pode ser lido. A matriz do mapeamento em si vive em {@code AdoMapperTest}.
 */
class AdoEventSourceParentTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static List<RawEvent> workItems(AdoRestClient client) {
    AdoEventSource source =
        new AdoEventSource(
            client,
            new FakeConfig(),
            new FakeStructure(List.of(new Repository("repoA", "orgX", "ProjP", "t:1", "Prod"))));
    var r = source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (p, s, c) -> {});
    assertThat(r.failures()).as("a coleta não deve falhar nestes cenários").isEmpty();
    return r.events().stream().filter(e -> e.type() == EventType.WORKITEM).toList();
  }

  @Test
  void aParentCollectedInTheSameBatchCostsNoExtraCall() {
    // A Task 2 pende da User Story 1, e as duas mudaram na janela — vieram no mesmo lote.
    WorkItemClient client =
        new WorkItemClient("1,2", wi("1", "User Story", "") + "," + wi("2", "Task", "1"));

    List<RawEvent> items = workItems(client);

    assertThat(client.batchLookups).as("nenhuma resolução extra de pai").isZero();
    assertThat(items)
        .filteredOn(e -> e.id().endsWith(":2"))
        .singleElement()
        .satisfies(e -> assertThat(e.detail().get("type")).isEqualTo("feature"));
  }

  @Test
  void twoTasksSharingAnOutOfBatchParentCostOneCall() {
    WorkItemClient client =
        new WorkItemClient("2,3", wi("2", "Task", "9") + "," + wi("3", "Task", "9"));
    client.parents = wi("9", "Tech Debt", "");

    List<RawEvent> items = workItems(client);

    assertThat(client.batchLookups).as("um lookup para o pai compartilhado").isEqualTo(1);
    assertThat(items)
        .extracting(e -> e.detail().get("type"))
        .containsExactlyInAnyOrder("tech_debt", "tech_debt");
  }

  @Test
  void anUnresolvableParentFallsBackAndTheSyncCompletes() {
    WorkItemClient client = new WorkItemClient("2", wi("2", "Task", "9"));
    client.failParentLookup = true;

    List<RawEvent> items = workItems(client);

    assertThat(items)
        .singleElement()
        .satisfies(e -> assertThat(e.detail().get("type")).isEqualTo("docs"));
  }

  /**
   * Só a Task 2 está no lote; o pai 8 é buscado e também é Task. O limite de um nível manda parar
   * aí — o avô 9 nunca é pedido, e a Task cai no default.
   */
  @Test
  void aTaskWhoseParentIsAlsoATaskDoesNotClimbFurther() {
    WorkItemClient client = new WorkItemClient("2", wi("2", "Task", "8"));
    client.parents =
        "{\"id\":\"8\",\"fields\":{\"System.WorkItemType\":\"Task\",\"System.Parent\":\"9\"}}";

    List<RawEvent> items = workItems(client);

    assertThat(client.batchLookups).as("um lookup para o pai, e nenhum para o avô").isEqualTo(1);
    assertThat(client.urls).as("o avô nunca é pedido").noneMatch(u -> u.contains("ids=9"));
    assertThat(items)
        .singleElement()
        .satisfies(e -> assertThat(e.detail().get("type")).isEqualTo("docs"));
  }

  /** Um projeto com os work items informados; conta os lookups de pai separadamente. */
  private static final class WorkItemClient implements AdoRestClient {
    private final String ids;
    private final String items;
    String parents = "";
    boolean failParentLookup;
    int batchLookups;
    final List<String> urls = new ArrayList<>();

    WorkItemClient(String ids, String items) {
      this.ids = ids;
      this.items = items;
    }

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      if (url.contains("/updates")) {
        return json("{\"value\":[]}");
      }
      if (url.contains("/wit/workitems?ids=")) {
        // O lookup de pai é o que pede só o tipo (e tolera id ausente).
        if (url.contains("errorPolicy=Omit")) {
          batchLookups++;
          if (failParentLookup) {
            throw new IllegalStateException("Azure DevOps -> HTTP 404");
          }
          return json("{\"value\":[" + parents + "]}");
        }
        return json("{\"value\":[" + items + "]}");
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      StringBuilder sb = new StringBuilder("{\"workItems\":[");
      String[] each = ids.split(",");
      for (int i = 0; i < each.length; i++) {
        sb.append(i > 0 ? "," : "").append("{\"id\":").append(each[i]).append("}");
      }
      return json(sb.append("]}").toString());
    }
  }

  /** Um work item do lote, com as datas que o mapper exige. */
  private static String wi(String id, String type, String parent) {
    return "{\"id\":\""
        + id
        + "\",\"fields\":{\"System.ChangedDate\":\"2026-06-10T10:00:00Z\","
        + "\"System.CreatedDate\":\"2026-06-01T10:00:00Z\",\"System.WorkItemType\":\""
        + type
        + "\""
        + (parent.isEmpty() ? "" : ",\"System.Parent\":\"" + parent + "\"")
        + "}}";
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
