package com.engperf.adapter.outbound.ado;

import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.ImpedimentConvention;

/**
 * Configuração de plataforma fixa: convenção de IA por trailer do Copilot. A convenção de
 * impedimento vem desligada por padrão — é o estado em que a maioria dos testes do adapter quer ler
 * as fases sem overlay — e o construtor de dois argumentos liga o sinal para quem o testa.
 */
final class FakeConfig implements PlatformConfigUseCase {

  private ImpedimentConvention impediment = ImpedimentConvention.off();

  FakeConfig() {}

  FakeConfig(String fieldName, String tagPrefix) {
    this.impediment = new ImpedimentConvention(fieldName, tagPrefix);
  }

  @Override
  public ImpedimentConvention impedimentConvention() {
    return impediment;
  }

  @Override
  public ImpedimentConvention saveImpedimentConvention(String fieldName, String tagPrefix) {
    impediment = new ImpedimentConvention(fieldName, tagPrefix);
    return impediment;
  }

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
