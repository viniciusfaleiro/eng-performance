package com.engperf.application.port.outbound;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.SmtpSettings;

/**
 * Outbound port: persistence for the singleton platform configuration (ADO connection, AI rule,
 * outgoing email server).
 */
public interface PlatformConfigPort {

  AdoIntegration getAdoIntegration();

  AdoIntegration saveAdoIntegration(AdoIntegration integration);

  AiConvention getAiConvention();

  AiConvention saveAiConvention(AiConvention convention);

  SmtpSettings getSmtpSettings();

  /**
   * Saves the email settings. When {@code settings.password()} is {@code null}, the previously
   * stored password (if any) is preserved rather than cleared.
   */
  SmtpSettings saveSmtpSettings(SmtpSettings settings);
}
