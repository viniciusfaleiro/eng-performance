package com.engperf.application.port.outbound;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;

/**
 * Outbound port: persistence for the singleton platform configuration (ADO connection, AI rule).
 */
public interface PlatformConfigPort {

  AdoIntegration getAdoIntegration();

  AdoIntegration saveAdoIntegration(AdoIntegration integration);

  AiConvention getAiConvention();

  AiConvention saveAiConvention(AiConvention convention);
}
