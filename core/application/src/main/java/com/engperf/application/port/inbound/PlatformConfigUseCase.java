package com.engperf.application.port.inbound;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.SmtpSettings;

/**
 * Inbound port: read the ADO integration marker, read/save the AI convention, and read/save/test
 * the outgoing email server configuration.
 */
public interface PlatformConfigUseCase {

  AdoIntegration adoIntegration();

  /** Records that a real Azure DevOps sync has run (so the dev seeder stands down). */
  AdoIntegration markAdoConnected();

  AiConvention aiConvention();

  AiConvention saveAiConvention(
      AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive);

  SmtpSettings smtpSettings();

  /**
   * Saves the email settings. Pass {@code settings.password()} as {@code null} to keep the
   * currently stored one unchanged.
   */
  SmtpSettings saveSmtpSettings(SmtpSettings settings);

  /**
   * Sends a test message to {@code to} using the currently saved configuration (or log fallback).
   */
  void sendTestEmail(String to);
}
