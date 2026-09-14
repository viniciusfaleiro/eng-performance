package com.engperf.adapter.outbound.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.engperf.application.email.EmailDeliveryException;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import org.junit.jupiter.api.Test;

class RoutingEmailSenderTest {

  private static SmtpSettings usable() {
    return new SmtpSettings(
        true,
        "localhost",
        1,
        MailTransport.NONE,
        null,
        null,
        "no-reply@empresa.com",
        "Eng Performance",
        "https://empresa.com");
  }

  private static SmtpSettings unusable() {
    return new SmtpSettings(false, null, null, MailTransport.NONE, null, null, null, null, null);
  }

  @Test
  void fallsBackToLogWhenNoUsableSmtpIsConfigured() {
    RoutingEmailSender sender = new RoutingEmailSender(new FakeConfig(unusable()));
    // No SMTP server is actually reachable at this "host", so a real SMTP attempt would throw;
    // the fact that this does not throw proves the log fallback was taken.
    sender.send("dest@empresa.com", "subject", "body");
  }

  @Test
  void attemptsSmtpWhenConfigurationIsUsableAndPropagatesFailure() {
    // port 1 on localhost refuses connections in any sandboxed/CI environment, so this exercises
    // the SMTP path and its failure propagation rather than a fallback to the log.
    RoutingEmailSender sender = new RoutingEmailSender(new FakeConfig(usable()));
    assertThatExceptionOfType(EmailDeliveryException.class)
        .isThrownBy(() -> sender.send("dest@empresa.com", "subject", "body"));
  }

  @Test
  void isUsableReflectsEnabledHostAndFromAddress() {
    assertThat(usable().isUsable()).isTrue();
    assertThat(unusable().isUsable()).isFalse();
  }

  private static final class FakeConfig implements PlatformConfigPort {
    private final SmtpSettings smtp;

    FakeConfig(SmtpSettings smtp) {
      this.smtp = smtp;
    }

    @Override
    public AdoIntegration getAdoIntegration() {
      return new AdoIntegration(false, null);
    }

    @Override
    public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
      return integration;
    }

    @Override
    public AiConvention getAiConvention() {
      return new AiConvention(AiStrategy.TRAILER, null, null, null, false);
    }

    @Override
    public AiConvention saveAiConvention(AiConvention convention) {
      return convention;
    }

    @Override
    public SmtpSettings getSmtpSettings() {
      return smtp;
    }

    @Override
    public SmtpSettings saveSmtpSettings(SmtpSettings settings) {
      return settings;
    }
  }
}
