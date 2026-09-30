package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * O que a ingestão guarda sobre o pai de um work item: a categoria que o filho herda dele, e a
 * aresta que permite perguntar "este item tem filhas?" — sem a qual não há como separar um
 * contêiner de trabalho do trabalho em si.
 */
class AdoMapperParentTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest(name = "{0} (pai {1}) → {2}")
  @CsvSource({
    "Bug,,bug",
    "User Story,,feature",
    "Feature,,feature",
    "Product Backlog Item,,feature",
    "Epic,,feature",
    "Tech Debt,,tech_debt",
    "tech debt,,tech_debt",
    "TECH DEBT,,tech_debt",
    "Documentation or Other,,docs",
    "Impediment,,docs",
    "Task,User Story,feature",
    "Task,Epic,feature",
    "Task,Bug,bug",
    "Task,Tech Debt,tech_debt",
    "Task,Documentation or Other,docs",
    "task,user story,feature",
    "Task,,docs",
    "Task,Task,docs",
    "Task,Coisa Estranha,docs",
  })
  void workTypeMapping(String adoType, String parentType, String expected) {
    assertThat(AdoMapper.workType(adoType, parentType)).isEqualTo(expected);
  }

  @Test
  void aTaskWithABlankParentTypeFallsBack() {
    assertThat(AdoMapper.workType("Task", null)).isEqualTo("docs");
    assertThat(AdoMapper.workType("Task", "  ")).isEqualTo("docs");
  }

  /** Fiação: o tipo do pai chega ao detalhe do evento, que é o que o painel consome. */
  @Test
  void aTaskEventCarriesTheParentsWorkType() {
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem-task.json"),
            json("{\"value\":[]}"),
            state -> Segment.ACTIVE,
            Instant.parse("2026-06-30T12:00:00Z"),
            "org",
            "Proj",
            "Tech Debt",
            "4711");

    assertThat(e.detail().get("type")).isEqualTo("tech_debt");
  }

  /**
   * O tipo cru vai junto da categoria: sem ele, revisar o mapeamento depende de adivinhar quais
   * tipos o time usa, porque os nossos dados só guardavam o resultado.
   */
  @Test
  void theEventKeepsTheRawAdoTypeAlongsideTheMappedCategory() {
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem-task.json"),
            json("{\"value\":[]}"),
            state -> Segment.ACTIVE,
            Instant.parse("2026-06-30T12:00:00Z"),
            "org",
            "Proj",
            "Tech Debt",
            "4711");

    assertThat(e.detail())
        .containsEntry("type", "tech_debt")
        .containsEntry("ado_type", "Task")
        .containsEntry("ado_parent_type", "Tech Debt")
        .containsEntry("parent_event_id", "wi:4711");
  }

  /**
   * A aresta fica no filho, então um pai que não conseguimos ler — deletado, sem permissão, ou de
   * um projeto que não ingerimos — não impede a ingestão nem apaga a informação de que o filho tem
   * pai. Sem isso, o item continuaria disputando hora com as próprias filhas por falta de metadado
   * do pai.
   */
  @Test
  void anInaccessibleParentStillLeavesTheEdgeOnTheChild() {
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem-task.json"),
            json("{\"value\":[]}"),
            state -> Segment.ACTIVE,
            Instant.parse("2026-06-30T12:00:00Z"),
            "org",
            "Proj",
            null,
            "4711");

    assertThat(e.detail())
        .containsEntry("parent_event_id", "wi:4711")
        .doesNotContainKey("ado_parent_type");
    assertThat(e.detail().get("type")).isEqualTo("docs"); // a herança de categoria é que se perde
  }

  @Test
  void aWorkItemWithoutAParentCarriesNoParentType() {
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem.json"),
            json("{\"value\":[]}"),
            state -> Segment.ACTIVE,
            Instant.parse("2026-06-30T12:00:00Z"),
            "org",
            "Proj",
            null,
            null);

    assertThat(e.detail())
        .containsEntry("ado_type", "Bug")
        .doesNotContainKey("ado_parent_type")
        .doesNotContainKey("parent_event_id");
  }

  private static JsonNode fixture(String name) {
    try {
      return JSON.readTree(AdoMapperParentTest.class.getResourceAsStream("/ado/" + name));
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
