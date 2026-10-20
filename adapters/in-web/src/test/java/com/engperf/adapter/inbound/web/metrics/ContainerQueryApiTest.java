package com.engperf.adapter.inbound.web.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.ContainerFilter;
import org.junit.jupiter.api.Test;

/**
 * A seleção de contêiner na API: ela chega ao serviço, e um valor inventado é 400.
 *
 * <p>Filtro silenciosamente ignorado responde uma pergunta que o leitor não fez, com números que
 * parecem perfeitamente certos — por isso o valor desconhecido é recusado em vez de descartado.
 */
class ContainerQueryApiTest {

  @Test
  void anAbsentSelectionMeansEverything() {
    assertThat(new ContainerQuery().filter()).isEqualTo(ContainerFilter.ALL);
    assertThat(new ContainerQuery("").filter()).isEqualTo(ContainerFilter.ALL);
  }

  @Test
  void eachSideIsAccepted() {
    assertThat(new ContainerQuery("leaves").filter()).isEqualTo(ContainerFilter.LEAVES);
    assertThat(new ContainerQuery("containers").filter()).isEqualTo(ContainerFilter.CONTAINERS);
    assertThat(new ContainerQuery("ALL").filter()).isEqualTo(ContainerFilter.ALL);
  }

  @Test
  void anUnknownSelectionIsRejected() {
    assertThat(
            org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> new ContainerQuery("epicos").filter(), IllegalArgumentException.class))
        .hasMessageContaining("epicos")
        .hasMessageContaining("leaves");
  }

  /**
   * O card diz se responde à seleção — é o que o filtro de tipo de trabalho já faz, e a falta disso
   * deixava PR Size e Commits parados sem explicação com "Folhas" ligado.
   */
  @Test
  void aMetricThatCannotBeFilteredSaysSo() {
    assertThat(definition(com.engperf.domain.metrics.EventType.WORKITEM).filterableByContainer())
        .isTrue();
    assertThat(definition(com.engperf.domain.metrics.EventType.PR).filterableByContainer())
        .isFalse();
    assertThat(definition(com.engperf.domain.metrics.EventType.DEPLOY).filterableByContainer())
        .isFalse();
  }

  private static com.engperf.domain.metrics.MetricDefinition definition(
      com.engperf.domain.metrics.EventType type) {
    return new com.engperf.domain.metrics.MetricDefinition(
        "m",
        "M",
        "fluxo",
        type,
        com.engperf.domain.metrics.AttributionScope.PERSON,
        com.engperf.domain.metrics.Aggregation.SUM,
        "itens",
        com.engperf.domain.metrics.Direction.HIGHER_BETTER);
  }
}
