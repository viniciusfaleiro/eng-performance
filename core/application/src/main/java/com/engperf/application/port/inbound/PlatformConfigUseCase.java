package com.engperf.application.port.inbound;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;

/** Inbound port: read/save the platform configuration (ADO connection and AI convention). */
public interface PlatformConfigUseCase {

  AdoIntegration adoIntegration();

  AdoIntegration saveAdoIntegration(String organizationUrl, String pat, String productionStageRule);

  /** Marks the connection as validated (real ADO check is S9). */
  AdoIntegration testAdoConnection();

  AiConvention aiConvention();

  AiConvention saveAiConvention(
      AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive);
}
