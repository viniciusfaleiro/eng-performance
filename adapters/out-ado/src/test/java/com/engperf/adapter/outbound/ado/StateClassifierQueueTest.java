package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Os estados reais dos boards do time, estado por estado.
 *
 * <p>A fila comprometida é o que separa "o time pegou o item e ele espera a próxima mão" de "o item
 * espera ser pego". Sem essa linha não há como dizer onde começa o trabalho comprometido — e ela
 * refina a espera sem mudar fase nenhuma: QUEUE e WAITING contam igual nas horas.
 */
class StateClassifierQueueTest {

  @ParameterizedTest(name = "{0} ({1}) → {2}")
  @CsvSource({
    // User Story / Bug / Tech Debt
    "Refinement,Proposed,WAITING",
    "Ready,Proposed,QUEUE",
    "Doing,InProgress,ACTIVE",
    "Code Review,InProgress,REVIEW",
    "Em homolog,InProgress,ACTIVE",
    "Pronto p/ Prod,InProgress,ACTIVE",
    "Done,Completed,DONE",
    // Epic / Feature / Task
    "New,Proposed,WAITING",
    "Active,InProgress,ACTIVE",
    "Em Desenvolvimento,InProgress,ACTIVE",
    "Resolved,Resolved,DONE",
    "Closed,Completed,DONE",
  })
  void boardStatesByCategory(String state, String category, String expected) {
    assertThat(StateClassifier.fromCategory(category, state)).hasToString(expected);
  }

  /** Sem a categoria do ADO, o nome decide — e a fila tem de continuar sendo fila. */
  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "Refinement,ACTIVE",
    "Ready,QUEUE",
    "Ready for dev,QUEUE",
    "Pronto p/ Prod,QUEUE",
    "Doing,ACTIVE",
    "Code Review,REVIEW",
    "Blocked,WAITING",
    "Backlog,WAITING",
    "New,WAITING",
    "Done,DONE",
  })
  void boardStatesByName(String state, String expected) {
    assertThat(StateClassifier.byName(state)).hasToString(expected);
  }

  /**
   * "Refinement" sem categoria cai em ACTIVE pelo padrão do heurístico — é o comportamento que já
   * existia e não é objeto desta mudança; fica fixado para que uma alteração futura seja
   * deliberada.
   */
  @Test
  void theNameFallbackDefaultIsUnchanged() {
    assertThat(StateClassifier.byName("Qualquer coisa")).isEqualTo(Segment.ACTIVE);
  }
}
