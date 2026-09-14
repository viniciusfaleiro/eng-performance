package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed adapter for {@link PlatformConfigPort} (each config a single row, id
 * "default"). The SMTP password never touches the database in clear text — {@link SecretCipher}
 * handles that at the edge of this adapter.
 */
@Component
@Transactional
public class JpaPlatformConfigRepository implements PlatformConfigPort {

  private static final String ID = "default";

  private final AdoIntegrationJpaRepository ado;
  private final AiConventionJpaRepository ai;
  private final SmtpSettingsJpaRepository smtp;
  private final SecretCipher cipher;

  public JpaPlatformConfigRepository(
      AdoIntegrationJpaRepository ado,
      AiConventionJpaRepository ai,
      SmtpSettingsJpaRepository smtp,
      SecretCipher cipher) {
    this.ado = ado;
    this.ai = ai;
    this.smtp = smtp;
    this.cipher = cipher;
  }

  @Override
  @Transactional(readOnly = true)
  public AdoIntegration getAdoIntegration() {
    return ado.findById(ID)
        .map(JpaPlatformConfigRepository::toAdo)
        .orElseGet(() -> new AdoIntegration(false, null));
  }

  @Override
  public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
    ado.save(new AdoIntegrationEntity(ID, integration.connected(), integration.lastValidatedAt()));
    return integration;
  }

  @Override
  @Transactional(readOnly = true)
  public AiConvention getAiConvention() {
    return ai.findById(ID)
        .map(JpaPlatformConfigRepository::toAi)
        .orElseGet(() -> new AiConvention(AiStrategy.TRAILER, null, null, null, false));
  }

  @Override
  public AiConvention saveAiConvention(AiConvention convention) {
    ai.save(
        new AiConventionEntity(
            ID,
            convention.strategy(),
            convention.trailer(),
            convention.tag(),
            convention.regex(),
            convention.caseSensitive()));
    return convention;
  }

  private static AdoIntegration toAdo(AdoIntegrationEntity e) {
    return new AdoIntegration(e.isConnected(), e.getLastValidatedAt());
  }

  private static AiConvention toAi(AiConventionEntity e) {
    return new AiConvention(
        e.getStrategy(), e.getTrailer(), e.getTag(), e.getRegex(), e.isCaseSensitive());
  }

  @Override
  @Transactional(readOnly = true)
  public SmtpSettings getSmtpSettings() {
    return smtp.findById(ID).map(this::toSmtp).orElseGet(this::emptySmtp);
  }

  @Override
  public SmtpSettings saveSmtpSettings(SmtpSettings settings) {
    String ciphertext =
        smtp.findById(ID).map(SmtpSettingsEntity::getPasswordCiphertext).orElse(null);
    if (settings.password() != null) {
      ciphertext = cipher.encrypt(settings.password());
    }
    SmtpSettingsEntity entity = new SmtpSettingsEntity(ID);
    entity.setEnabled(settings.enabled());
    entity.setHost(settings.host());
    entity.setPort(settings.port());
    entity.setTransport(settings.transport());
    entity.setUsername(settings.username());
    entity.setPasswordCiphertext(ciphertext);
    entity.setFromAddress(settings.fromAddress());
    entity.setFromName(settings.fromName());
    entity.setAppBaseUrl(settings.appBaseUrl());
    smtp.save(entity);
    return getSmtpSettings();
  }

  private SmtpSettings toSmtp(SmtpSettingsEntity e) {
    return new SmtpSettings(
        e.isEnabled(),
        e.getHost(),
        e.getPort(),
        e.getTransport(),
        e.getUsername(),
        cipher.decrypt(e.getPasswordCiphertext()),
        e.getFromAddress(),
        e.getFromName(),
        e.getAppBaseUrl());
  }

  private SmtpSettings emptySmtp() {
    return new SmtpSettings(false, null, null, MailTransport.NONE, null, null, null, null, null);
  }
}
