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
  public AdoIntegration markAdoConnected() {
    return port.saveAdoIntegration(new AdoIntegration(true, Instant.now()));
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
