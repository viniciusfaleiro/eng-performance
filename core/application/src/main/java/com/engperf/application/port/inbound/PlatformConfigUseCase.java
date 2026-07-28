package com.engperf.application.port.inbound;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;

/** Inbound port: read the ADO integration marker and read/save the AI convention. */
public interface PlatformConfigUseCase {

  AdoIntegration adoIntegration();

  /** Records that a real Azure DevOps sync has run (so the dev seeder stands down). */
  AdoIntegration markAdoConnected();

  AiConvention aiConvention();

  AiConvention saveAiConvention(
      AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive);
}
