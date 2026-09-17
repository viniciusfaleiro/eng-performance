package com.engperf.adapter.outbound.ado;

import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;

/** Configuração de plataforma fixa: convenção de IA por trailer do Copilot. */
final class FakeConfig implements PlatformConfigUseCase {
  @Override
  public AdoIntegration adoIntegration() {
    return new AdoIntegration(true, null);
  }

  @Override
  public AdoIntegration markAdoConnected() {
    return adoIntegration();
  }

  @Override
  public AiConvention aiConvention() {
    return new AiConvention(AiStrategy.TRAILER, "Co-authored-by: Copilot", null, null, false);
  }

  @Override
  public AiConvention saveAiConvention(
      AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive) {
    return aiConvention();
  }

  @Override
  public com.engperf.domain.config.SmtpSettings smtpSettings() {
    return new com.engperf.domain.config.SmtpSettings(
        false,
        null,
        null,
        com.engperf.domain.config.MailTransport.NONE,
        null,
        null,
        null,
        null,
        null);
  }

  @Override
  public com.engperf.domain.config.SmtpSettings saveSmtpSettings(
      com.engperf.domain.config.SmtpSettings settings) {
    return smtpSettings();
  }

  @Override
  public void sendTestEmail(String to) {}
}
