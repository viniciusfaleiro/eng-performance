package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The work-type vocabulary, its Azure DevOps mapping and the reading filter built on them. */
class WorkTypeTest {

  @Test
  @DisplayName("as cinco categorias, na ordem da legenda")
  void fiveCategoriesInLegendOrder() {
    assertThat(WorkType.all().stream().map(WorkType::key))
        .containsExactly("feature", "bug", "tech_debt", "maintenance", "docs");
  }

  @Test
  @DisplayName("a chave é lida sem distinção de caixa, e chave desconhecida não vira categoria")
  void keyLookup() {
    assertThat(WorkType.byKey("TECH_DEBT")).contains(WorkType.TECH_DEBT);
    assertThat(WorkType.byKey("nada")).isEmpty();
    assertThat(WorkType.byKey(null)).isEmpty();
  }

  @Test
  @DisplayName("o de-para cobre os tipos padrão, os customizados e manutenção")
  void mappingTable() {
    assertThat(WorkTypeMapping.of("Bug")).isEqualTo(WorkType.BUG);
    assertThat(WorkTypeMapping.of("User Story")).isEqualTo(WorkType.FEATURE);
    assertThat(WorkTypeMapping.of("epic")).isEqualTo(WorkType.FEATURE);
    assertThat(WorkTypeMapping.of("Tech Debt")).isEqualTo(WorkType.TECH_DEBT);
    assertThat(WorkTypeMapping.of("Chore")).isEqualTo(WorkType.MAINTENANCE);
    assertThat(WorkTypeMapping.of("Documentation or Other")).isEqualTo(WorkType.DOCS);
  }

  @Test
  @DisplayName("tipo fora da tabela cai no padrão, mas não se confunde com documentação declarada")
  void unmappedFallsBackButIsVisible() {
    assertThat(WorkTypeMapping.of("Spike")).isEqualTo(WorkType.DOCS);
    assertThat(WorkTypeMapping.isMapped("Spike")).isFalse();
    assertThat(WorkTypeMapping.isMapped("Documentation or Other")).isTrue();
    assertThat(WorkTypeMapping.isMapped(" ")).isFalse();
  }

  @Test
  @DisplayName("seleção vazia leva tudo, inclusive o evento sem tipo")
  void emptySelectionTakesEverything() {
    assertThat(WorkTypeFilter.none().matches(workItem("bug"))).isTrue();
    assertThat(WorkTypeFilter.none().matches(untypedPr())).isTrue();
    assertThat(WorkTypeFilter.none().isEmpty()).isTrue();
  }

  @Test
  @DisplayName("um tipo selecionado leva só os eventos daquele tipo")
  void singleType() {
    WorkTypeFilter f = WorkTypeFilter.of(WorkType.BUG);
    assertThat(f.matches(workItem("bug"))).isTrue();
    assertThat(f.matches(workItem("feature"))).isFalse();
  }

  @Test
  @DisplayName("vários tipos selecionados levam a união, e o PR casa por interseção")
  void manyTypes() {
    WorkTypeFilter f = WorkTypeFilter.of(WorkType.BUG, WorkType.TECH_DEBT);
    assertThat(f.matches(workItem("tech_debt"))).isTrue();
    assertThat(f.matches(pr("feature,bug"))).isTrue();
    assertThat(f.matches(pr("feature,docs"))).isFalse();
    assertThat(f.keys()).containsExactly("bug", "tech_debt");
  }

  @Test
  @DisplayName("evento sem tipo nenhum não casa com seleção alguma, e é reconhecível")
  void untypedMatchesNothing() {
    assertThat(WorkTypeFilter.of(WorkType.BUG).matches(untypedPr())).isFalse();
    assertThat(WorkTypeFilter.isUntyped(untypedPr())).isTrue();
    assertThat(WorkTypeFilter.isUntyped(workItem("docs"))).isFalse();
  }

  @Test
  @DisplayName("só as métricas de deploy ficam fora do filtro")
  void onlyDeployMetricsAreUnfilterable() {
    assertThat(definition(EventType.DEPLOY).filterableByWorkType()).isFalse();
    for (EventType t : EventType.values()) {
      if (t != EventType.DEPLOY) {
        assertThat(definition(t).filterableByWorkType()).as(t.name()).isTrue();
      }
    }
  }

  /**
   * Contêiner × folha só existe para quem é work item: deploy pertence a um pipeline e pull request
   * a um repositório, e nenhum dos dois agrupa work item. Quem não responde tem de poder dizer isso
   * — filtro que some em silêncio é o que faz o leitor desconfiar do número certo.
   */
  @Test
  @DisplayName("só as métricas de work item respondem ao filtro de contêiner")
  void onlyWorkItemMetricsAreContainerFilterable() {
    assertThat(definition(EventType.WORKITEM).filterableByContainer()).isTrue();
    for (EventType t : EventType.values()) {
      if (t != EventType.WORKITEM) {
        assertThat(definition(t).filterableByContainer()).as(t.name()).isFalse();
      }
    }
  }

  private static MetricDefinition definition(EventType type) {
    return new MetricDefinition(
        "m",
        "M",
        "g",
        type,
        AttributionScope.PERSON,
        Aggregation.SUM,
        "x",
        Direction.HIGHER_BETTER);
  }

  private static RawEvent workItem(String type) {
    return event(EventType.WORKITEM, Map.of("type", type));
  }

  private static RawEvent pr(String types) {
    return event(EventType.PR, Map.of("work_types", types));
  }

  private static RawEvent untypedPr() {
    return event(EventType.PR, Map.of());
  }

  private static RawEvent event(EventType type, Map<String, String> detail) {
    return new RawEvent(
        "e1", type, Instant.parse("2026-01-01T00:00:00Z"), null, "a@b", null, null, false, detail);
  }
}
