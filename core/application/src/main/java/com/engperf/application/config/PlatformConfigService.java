package com.engperf.application.config;

import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import java.time.Instant;
import java.util.Objects;

/** Application service for the singleton platform configuration (ADO connection, AI convention). */
public final class PlatformConfigService implements PlatformConfigUseCase {

  private final PlatformConfigPort port;

  public PlatformConfigService(PlatformConfigPort port) {
    this.port = Objects.requireNonNull(port, "port must not be null");
  }

  @Override
  public AdoIntegration adoIntegration() {
    return port.getAdoIntegration();
  }

  @Override
  public AdoIntegration saveAdoIntegration(
      String organizationUrl, String pat, String productionStageRule) {
    AdoIntegration current = port.getAdoIntegration();
    // Keep the stored secret when the PAT field is left blank (never overwrite with nothing).
    String secret = (pat == null || pat.isBlank()) ? current.patSecret() : pat;
    return port.saveAdoIntegration(
        new AdoIntegration(
            organizationUrl,
            secret,
            productionStageRule,
            current.connected(),
            current.lastValidatedAt()));
  }

  @Override
  public AdoIntegration testAdoConnection() {
    AdoIntegration current = port.getAdoIntegration();
    return port.saveAdoIntegration(
        new AdoIntegration(
            current.organizationUrl(),
            current.patSecret(),
            current.productionStageRule(),
            true,
            Instant.now()));
  }

  @Override
  public AiConvention aiConvention() {
    return port.getAiConvention();
  }

  @Override
  public AiConvention saveAiConvention(
      AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive) {
    return port.saveAiConvention(new AiConvention(strategy, trailer, tag, regex, caseSensitive));
  }
}
