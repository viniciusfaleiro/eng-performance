package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;

/**
 * Resolver o pai de cada Task individualmente multiplicaria o custo da sincronização pelo tipo mais
 * numeroso do board. Estes testes fixam o contrato que evita isso: lote, dedup e degradação.
 */
class ParentTypeResolverTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void parentsAlreadyCollectedCostNothing() {
    RecordingClient client = new RecordingClient();
    Map<String, String> typeById = new HashMap<>(Map.of("10", "User Story"));

    ParentTypeResolver.resolve(client, "org", List.of(task("1", "10")), typeById, "tok", 200);

    assertThat(client.urls).isEmpty();
    assertThat(typeById).containsEntry("10", "User Story");
  }

  @Test
  void tasksSharingAParentCostOneLookup() {
    RecordingClient client = new RecordingClient();
    Map<String, String> typeById = new HashMap<>();

    ParentTypeResolver.resolve(
        client,
        "org",
        List.of(task("1", "10"), task("2", "10"), task("3", "10")),
        typeById,
        "tok",
        200);

    assertThat(client.urls).hasSize(1);
    assertThat(client.urls.get(0)).contains("ids=10").contains("errorPolicy=Omit");
    assertThat(typeById).containsEntry("10", "Bug");
  }

  @Test
  void missingParentsArePagedAtTheBatchSize() {
    RecordingClient client = new RecordingClient();
    Map<String, String> typeById = new HashMap<>();
    List<JsonNode> items = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      items.add(task("t" + i, "p" + i));
    }

    ParentTypeResolver.resolve(client, "org", items, typeById, "tok", 200);

    assertThat(client.urls).as("250 pais → 200 + 50").hasSize(2);
    assertThat(typeById).hasSize(250);
  }

  /** Um pai apagado ou inacessível não pode custar a ingestão inteira. */
  @Test
  void aFailedLookupLeavesTheIndexEmptyAndDoesNotThrow() {
    AdoRestClient failing =
        new AdoRestClient() {
          @Override
          public JsonNode get(String url, String token) {
            throw new IllegalStateException("Azure DevOps -> HTTP 403");
          }

          @Override
          public JsonNode post(String url, String token, String body) {
            throw new UnsupportedOperationException();
          }
        };
    Map<String, String> typeById = new HashMap<>();

    ParentTypeResolver.resolve(failing, "org", List.of(task("1", "10")), typeById, "tok", 200);

    assertThat(typeById).isEmpty();
  }

  @Test
  void aSelfParentIsNotLookedUp() {
    RecordingClient client = new RecordingClient();

    ParentTypeResolver.resolve(
        client, "org", List.of(task("10", "10")), new HashMap<>(), "tok", 200);

    assertThat(client.urls).isEmpty();
  }

  @Test
  void anItemWithoutAParentIsIgnored() {
    RecordingClient client = new RecordingClient();

    ParentTypeResolver.resolve(client, "org", List.of(task("1", "")), new HashMap<>(), "tok", 200);

    assertThat(client.urls).isEmpty();
  }

  /** Devolve "Bug" para cada id pedido, para o teste conferir a mesclagem. */
  private static final class RecordingClient implements AdoRestClient {
    final List<String> urls = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      String ids = url.substring(url.indexOf("ids=") + 4, url.indexOf("&fields="));
      StringJoiner out = new StringJoiner(",", "{\"value\":[", "]}");
      for (String id : ids.split(",")) {
        out.add("{\"id\":\"" + id + "\",\"fields\":{\"System.WorkItemType\":\"Bug\"}}");
      }
      return json(out.toString());
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      throw new UnsupportedOperationException();
    }
  }

  private static JsonNode task(String id, String parent) {
    return json(
        "{\"id\":\""
            + id
            + "\",\"fields\":{\"System.WorkItemType\":\"Task\",\"System.Parent\":\""
            + parent
            + "\"}}");
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
