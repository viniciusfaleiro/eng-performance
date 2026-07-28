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
  void saveAdoKeepsSecretWhenPatBlank() {
    service.saveAdoIntegration("https://dev.azure.com/org", "pat-1", "prod");
    // update org but leave PAT blank -> keeps the stored secret
    AdoIntegration after = service.saveAdoIntegration("https://dev.azure.com/org2", "  ", "prod2");
    assertThat(after.organizationUrl()).isEqualTo("https://dev.azure.com/org2");
    assertThat(after.patSecret()).isEqualTo("pat-1");
  }

  @Test
  void testConnectionMarksConnected() {
    service.saveAdoIntegration("https://dev.azure.com/org", "pat", "prod");
    AdoIntegration tested = service.testAdoConnection();
    assertThat(tested.connected()).isTrue();
    assertThat(tested.lastValidatedAt()).isNotNull();
  }

  @Test
  void savesAiConvention() {
    AiConvention c = service.saveAiConvention(AiStrategy.TAG, null, "[ai]", "(?i)\\[ai\\]", true);
    assertThat(c.strategy()).isEqualTo(AiStrategy.TAG);
    assertThat(c.tag()).isEqualTo("[ai]");
    assertThat(service.aiConvention().tag()).isEqualTo("[ai]");
  }

  private static final class FakeConfig implements PlatformConfigPort {
    private AdoIntegration ado = new AdoIntegration(null, null, null, false, null);
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
