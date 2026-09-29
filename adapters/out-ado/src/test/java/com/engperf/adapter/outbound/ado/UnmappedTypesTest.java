package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Um tipo novo no board não pode sumir calado dentro de "docs/outros". */
class UnmappedTypesTest {

  @Test
  void countsEachUnrecognisedTypeSeparately() {
    UnmappedTypes t = new UnmappedTypes();
    t.record("Impediment");
    t.record("Impediment");
    t.record("Test Case");

    assertThat(t.counts())
        .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("Impediment", 2, "Test Case", 1));
  }

  @Test
  void aBlankTypeIsNothingToReport() {
    UnmappedTypes t = new UnmappedTypes();
    t.record("");
    t.record(null);

    assertThat(t.counts()).isEmpty();
  }

  /** Reportar só quando há o que dizer — o caso comum é o silêncio. */
  @Test
  void reportingWithNothingRecordedDoesNotFail() {
    new UnmappedTypes().report("org", "Proj");
  }
}
