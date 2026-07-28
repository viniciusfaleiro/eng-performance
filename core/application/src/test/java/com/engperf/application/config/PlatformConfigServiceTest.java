package com.engperf.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import org.junit.jupiter.api.Test;

class PlatformConfigServiceTest {

  private final FakeConfig port = new FakeConfig();
  private final PlatformConfigService service = new PlatformConfigService(port);

  @Test
  void markAdoConnectedSetsTheMarker() {
    assertThat(service.adoIntegration().connected()).isFalse();
    AdoIntegration marked = service.markAdoConnected();
    assertThat(marked.connected()).isTrue();
    assertThat(marked.lastValidatedAt()).isNotNull();
    assertThat(service.adoIntegration().connected()).isTrue();
  }

  @Test
  void savesAiConvention() {
    AiConvention c = service.saveAiConvention(AiStrategy.TAG, null, "[ai]", "(?i)\\[ai\\]", true);
    assertThat(c.strategy()).isEqualTo(AiStrategy.TAG);
    assertThat(c.tag()).isEqualTo("[ai]");
    assertThat(service.aiConvention().tag()).isEqualTo("[ai]");
  }

  private static final class FakeConfig implements PlatformConfigPort {
    private AdoIntegration ado = new AdoIntegration(false, null);
    private AiConvention ai =
        new AiConvention(AiStrategy.TRAILER, "Co-authored-by:", null, null, false);

    @Override
    public AdoIntegration getAdoIntegration() {
      return ado;
    }

    @Override
    public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
      this.ado = integration;
      return integration;
    }

    @Override
    public AiConvention getAiConvention() {
      return ai;
    }

    @Override
    public AiConvention saveAiConvention(AiConvention convention) {
      this.ai = convention;
      return convention;
    }
  }
}
