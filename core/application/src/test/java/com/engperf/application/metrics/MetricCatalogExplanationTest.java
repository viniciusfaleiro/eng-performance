package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.MetricDefinition;
import com.engperf.domain.metrics.MetricExplanation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the join between a metric and the text that explains it.
 *
 * <p>What this can prove is existence, not truthfulness: nothing here stops an explanation from
 * describing a calculation the engine no longer does. It catches the failure that actually happens
 * — a metric gets added and nobody writes its text — which is how {@code commit_count} and {@code
 * pr_count} would have shipped with an empty modal.
 */
class MetricCatalogExplanationTest {

  private final MetricCatalog catalog = new MetricCatalog();

  @Test
  void everyPublishedMetricIsExplained() {
    List<String> unexplained =
        catalog.all().stream()
            .filter(d -> d.explained().isEmpty())
            .map(MetricDefinition::key)
            .toList();

    assertThat(unexplained)
        .as("métricas sem explicação — escreva o texto em MetricExplanations")
        .isEmpty();
  }

  @Test
  void noExplanationPointsAtAMetricThatDoesNotExist() {
    List<String> published = catalog.all().stream().map(MetricDefinition::key).toList();
    List<String> orphans =
        MetricExplanations.texts().keySet().stream()
            .filter(key -> !published.contains(key) && !"ai_impact".equals(key))
            .toList();

    assertThat(orphans)
        .as("explicações órfãs — a métrica saiu do catálogo e o texto ficou")
        .isEmpty();
  }

  /**
   * An explanation that says only what the metric is worth little; the fields that change a
   * reader's mind are what gets left out and a number they can follow.
   */
  @Test
  void everyExplanationCarriesItsBoundariesAndAWorkedExample() {
    assertThat(catalog.all())
        .allSatisfy(
            d -> {
              MetricExplanation e = d.explained().orElseThrow();
              assertThat(e.rule()).as("regra de %s", d.key()).isNotBlank();
              assertThat(e.source()).as("fonte de %s", d.key()).isNotBlank();
              assertThat(e.included()).as("inclusões de %s", d.key()).isNotBlank();
              assertThat(e.excluded()).as("exclusões de %s", d.key()).isNotBlank();
              assertThat(e.placement()).as("colocação no tempo de %s", d.key()).isNotBlank();
              assertThat(e.example()).as("exemplo de %s", d.key()).isNotBlank();
            });
  }

  /**
   * O texto do WIP prometia "itens que estiveram em andamento em algum momento do período" enquanto
   * a conta contava itens cuja última alteração caiu no período. As duas leituras cabem na mesma
   * frase, então nada na tela podia revelar a divergência.
   *
   * <p>Isto não prova que o texto está certo — nenhum teste prova isso. Prende o vocabulário em que
   * a divergência se esconde: a colocação no tempo do WIP tem de falar de intervalo, e não pode
   * dizer que item concluído fica de fora, porque agora ele conta.
   */
  @Test
  void theWipExplanationDescribesTheIntervalRuleAndNotTheOldOne() {
    MetricExplanation wip =
        catalog.all().stream()
            .filter(d -> d.key().equals("wip"))
            .findFirst()
            .orElseThrow()
            .explained()
            .orElseThrow();

    assertThat(wip.placement()).containsIgnoringCase("intervalo").containsIgnoringCase("atravess");
    assertThat(wip.placement())
        .as("a não-aditividade precisa estar dita onde o número é lido")
        .containsIgnoringCase("não é aditiva");
    assertThat(wip.excluded())
        .as("item concluído dentro do período passou a contar")
        .doesNotContainIgnoringCase("Itens concluídos e");
    assertThat(wip.included()).containsIgnoringCase("concluídos dentro dele");
  }

  /** Quem conta só item concluído tem de dizer isso na colocação no tempo. */
  @Test
  void theCompletedOnlyMetricsSayTheyArePlacedByCompletion() {
    for (String key : List.of("flow_efficiency", "active_time", "waiting_time", "review_time")) {
      MetricExplanation e =
          catalog.all().stream()
              .filter(d -> d.key().equals(key))
              .findFirst()
              .orElseThrow()
              .explained()
              .orElseThrow();
      assertThat(e.placement()).as("colocação no tempo de %s", key).containsIgnoringCase("concluí");
    }
  }

  @Test
  void theAiImpactCompositeIsExplainedToo() {
    assertThat(MetricCatalog.AI_IMPACT.explained()).isPresent();
  }

  @Test
  void attributionIsStatedOnceForEveryMetric() {
    assertThat(catalog.attributionNote()).contains("time em que a pessoa estava");
  }
}
