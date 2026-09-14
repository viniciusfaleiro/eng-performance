package com.engperf.adapter.outbound.email;

import com.engperf.application.port.outbound.EmailSenderPort;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.SmtpSettings;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The single {@link EmailSenderPort} bean. Reads the current {@link SmtpSettings} on every send —
 * not once at startup — so saving the configuration in the Admin screen takes effect immediately. A
 * configured server that fails to deliver propagates the error; only the *absence* of a usable
 * configuration falls back to the log.
 */
@Component
public final class RoutingEmailSender implements EmailSenderPort {

  private final PlatformConfigPort config;

  public RoutingEmailSender(PlatformConfigPort config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  @Override
  public void send(String to, String subject, String body) {
    SmtpSettings settings = config.getSmtpSettings();
    if (settings.isUsable()) {
      new SmtpEmailSender(settings).send(to, subject, body);
    } else {
      new LogEmailSender().send(to, subject, body);
    }
  }
}
