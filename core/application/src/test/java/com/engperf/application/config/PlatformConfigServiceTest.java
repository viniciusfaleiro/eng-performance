package com.engperf.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.outbound.EmailSenderPort;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import org.junit.jupiter.api.Test;

class PlatformConfigServiceTest {

  private final FakeConfig port = new FakeConfig();
  private final FakeEmail email = new FakeEmail();
  private final PlatformConfigService service = new PlatformConfigService(port, email);

  @Test
  void markAdoConnectedSetsTheMarker() {
    assertThat(service.adoIntegration().connected()).isFalse();
    AdoIntegration marked = service.markAdoConnected();
    assertThat(marked.connected()).isTrue();
    assertThat(marked.lastValidatedAt()).isNotNull();
    assertThat(service.adoIntegration().connected()).isTrue();
  }

  @Test
  void savesAiConvention() {
    AiConvention c = service.saveAiConvention(AiStrategy.TAG, null, "[ai]", "(?i)\\[ai\\]", true);
    assertThat(c.strategy()).isEqualTo(AiStrategy.TAG);
    assertThat(c.tag()).isEqualTo("[ai]");
    assertThat(service.aiConvention().tag()).isEqualTo("[ai]");
  }

  @Test
  void savesSmtpSettingsAndReadsThemBack() {
    SmtpSettings saved =
        service.saveSmtpSettings(
            new SmtpSettings(
                true,
                "smtp.empresa.com",
                587,
                MailTransport.STARTTLS,
                "no-reply",
                "secret",
                "no-reply@empresa.com",
                "Eng Performance",
                "https://empresa.com"));
    assertThat(saved.host()).isEqualTo("smtp.empresa.com");
    assertThat(service.smtpSettings().host()).isEqualTo("smtp.empresa.com");
  }

  @Test
  void sendsTestEmailThroughThePort() {
    service.sendTestEmail("dest@empresa.com");
    assertThat(email.to).isEqualTo("dest@empresa.com");
  }

  private static final class FakeConfig implements PlatformConfigPort {
    private AdoIntegration ado = new AdoIntegration(false, null);
    private AiConvention ai =
        new AiConvention(AiStrategy.TRAILER, "Co-authored-by:", null, null, false);
    private SmtpSettings smtp =
        new SmtpSettings(false, null, null, MailTransport.NONE, null, null, null, null, null);

    @Override
    public AdoIntegration getAdoIntegration() {
      return ado;
    }

    @Override
    public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
      this.ado = integration;
      return integration;
    }

    @Override
    public AiConvention getAiConvention() {
      return ai;
    }

    @Override
    public AiConvention saveAiConvention(AiConvention convention) {
      this.ai = convention;
      return convention;
    }

    @Override
    public SmtpSettings getSmtpSettings() {
      return smtp;
    }

    @Override
    public SmtpSettings saveSmtpSettings(SmtpSettings settings) {
      this.smtp = settings;
      return settings;
    }
  }

  private static final class FakeEmail implements EmailSenderPort {
    String to;

    @Override
    public void send(String to, String subject, String body) {
      this.to = to;
    }
  }
}
