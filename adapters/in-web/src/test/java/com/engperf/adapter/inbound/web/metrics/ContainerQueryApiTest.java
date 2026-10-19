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
}
