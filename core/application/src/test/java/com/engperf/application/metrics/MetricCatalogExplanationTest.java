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

    assertThat(wip.placement())
        .containsIgnoringCase("trecho comprometido")
        .containsIgnoringCase("atravess");
    assertThat(wip.placement())
        .as("a não-aditividade precisa estar dita onde o número é lido")
        .containsIgnoringCase("não é aditiva");
    assertThat(wip.placement())
        .as("e por que, por causa dela, a métrica só existe no diário")
        .containsIgnoringCase("Diário");
    assertThat(wip.excluded())
        .as("item concluído dentro do período passou a contar")
        .doesNotContainIgnoringCase("Itens concluídos e");
    assertThat(wip.excluded())
        .as("tempo impedido não é trabalho em andamento")
        .containsIgnoringCase("impedido");
    assertThat(wip.included())
        .as("fila de entrega conta, e é metade do motivo da métrica existir")
        .containsIgnoringCase("fila");
    assertThat(wip.included())
        .as("contêiner conta por padrão, e dá para ler só as folhas")
        .containsIgnoringCase("ntêiner");
  }

  /**
   * O card mostrava "linhas" e media contagem de commits, porque `changeCounts` nunca chega numa
   * lista de commits e o mapper caía num fallback silencioso. Linha alterada não existe no Git REST
   * do Azure DevOps, então a unidade honesta é arquivo — e o texto não pode voltar a prometer
   * linha.
   */
  @Test
  void thePrSizeExplanationPromisesFilesAndNotLines() {
    MetricDefinition d =
        catalog.all().stream().filter(x -> x.key().equals("pr_size")).findFirst().orElseThrow();
    MetricExplanation e = d.explained().orElseThrow();

    assertThat(d.unit()).isEqualTo("arquivos");
    assertThat(d.measure()).isEqualTo("files");
    assertThat(e.rule()).containsIgnoringCase("arquivos alterados");
    assertThat(e.rule())
        .as("a ausência de linhas na fonte precisa estar dita, não só implícita")
        .containsIgnoringCase("não é medida em linhas");
    assertThat(e.excluded())
        .as("sem contagem é sem dado, e a ressalva da pasta fica junto")
        .containsIgnoringCase("sem dado")
        .containsIgnoringCase("pasta");
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

  /**
   * As fases deixaram de ser mediana por fase e passaram a ser a participação aplicada ao card. O
   * texto tem de dizer isso e dizer quem fica de fora — item fechado sem passar por trabalho —,
   * porque é a diferença entre "a conta está errada" e "estes itens não têm ciclo".
   */
  @Test
  void thePhaseExplanationsStateThePopulationAndTheComposition() {
    for (String key : List.of("waiting_time", "active_time", "review_time")) {
      MetricExplanation e =
          catalog.all().stream()
              .filter(d -> d.key().equals(key))
              .findFirst()
              .orElseThrow()
              .explained()
              .orElseThrow();
      assertThat(e.rule()).as("regra de %s", key).containsIgnoringCase("Cycle Time");
      assertThat(e.included())
          .as("população de %s", key)
          .containsIgnoringCase("estado de trabalho");
    }
  }
}
