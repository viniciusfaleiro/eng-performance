package com.engperf.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void aiConventionNormalizesOptionalsAndRequiresStrategy() {
    AiConvention c =
        new AiConvention(AiStrategy.TRAILER, " Co-authored-by: Copilot ", "  ", null, true);
    assertThat(c.trailer()).isEqualTo("Co-authored-by: Copilot");
    assertThat(c.tag()).isNull();
    assertThat(c.caseSensitive()).isTrue();
    assertThatNullPointerException()
        .isThrownBy(() -> new AiConvention(null, null, null, null, false));
  }

  @Test
  void adoIntegrationIsAConnectionMarker() {
    assertThat(new AdoIntegration(false, null).connected()).isFalse();

    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    AdoIntegration connected = new AdoIntegration(true, now);
    assertThat(connected.connected()).isTrue();
    assertThat(connected.lastValidatedAt()).isEqualTo(now);
  }
}
