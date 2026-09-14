package com.engperf.adapter.inbound.web.admin;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import java.time.Instant;
import java.util.Locale;

/**
 * Request/response DTOs for the platform-config admin endpoints (ADO connection, AI convention,
 * outgoing email server).
 */
final class ConfigDtos {

  private ConfigDtos() {}

  record AdoView(boolean connected, Instant lastValidatedAt) {

    static AdoView from(AdoIntegration a) {
      return new AdoView(a.connected(), a.lastValidatedAt());
    }
  }

  record AiView(String strategy, String trailer, String tag, String regex, boolean caseSensitive) {

    static AiView from(AiConvention c) {
      return new AiView(
          c.strategy().name().toLowerCase(Locale.ROOT),
          c.trailer(),
          c.tag(),
          c.regex(),
          c.caseSensitive());
    }
  }

  record AiRequest(
      String strategy, String trailer, String tag, String regex, boolean caseSensitive) {}

  /**
   * {@code passwordSet} tells the UI whether a password is stored — the password itself never is.
   */
  record EmailView(
      boolean enabled,
      String host,
      Integer port,
      String transport,
      String username,
      boolean passwordSet,
      String fromAddress,
      String fromName,
      String appBaseUrl) {

    static EmailView from(SmtpSettings s) {
      return new EmailView(
          s.enabled(),
          s.host(),
          s.port(),
          s.transport().name().toLowerCase(Locale.ROOT),
          s.username(),
          s.password() != null,
          s.fromAddress(),
          s.fromName(),
          s.appBaseUrl());
    }
  }

  /** {@code password} is optional: omit (or send blank) to keep the currently stored one. */
  record EmailRequest(
      boolean enabled,
      String host,
      Integer port,
      String transport,
      String username,
      String password,
      String fromAddress,
      String fromName,
      String appBaseUrl) {

    MailTransport transportOrDefault() {
      return transport == null
          ? MailTransport.NONE
          : MailTransport.valueOf(transport.strip().toUpperCase(Locale.ROOT));
    }
  }

  record TestEmailRequest(String to) {}
}
