package com.engperf.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void aiConventionNormalizesOptionalsAndRequiresStrategy() {
    AiConvention c =
        new AiConvention(AiStrategy.TRAILER, " Co-authored-by: Copilot ", "  ", null, true);
    assertThat(c.trailer()).isEqualTo("Co-authored-by: Copilot");
    assertThat(c.tag()).isNull();
    assertThat(c.caseSensitive()).isTrue();
    assertThatNullPointerException()
        .isThrownBy(() -> new AiConvention(null, null, null, null, false));
  }

  @Test
  void adoIntegrationIsAConnectionMarker() {
    assertThat(new AdoIntegration(false, null).connected()).isFalse();

    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    AdoIntegration connected = new AdoIntegration(true, now);
    assertThat(connected.connected()).isTrue();
    assertThat(connected.lastValidatedAt()).isEqualTo(now);
  }

  private static SmtpSettings smtp(boolean enabled) {
    return new SmtpSettings(
        enabled,
        "smtp.empresa.com",
        587,
        MailTransport.STARTTLS,
        "no-reply",
        "secret",
        "no-reply@empresa.com",
        "Eng Performance",
        "https://empresa.com/eng-performance/");
  }

  @Test
  void smtpSettingsNormalizesBaseUrlAndReportsUsability() {
    SmtpSettings s = smtp(true);
    assertThat(s.appBaseUrl()).isEqualTo("https://empresa.com/eng-performance");
    assertThat(s.isUsable()).isTrue();
    assertThat(smtp(false).isUsable()).isFalse();
    assertThat(
            new SmtpSettings(true, null, null, MailTransport.NONE, null, null, null, null, null)
                .isUsable())
        .isFalse();
  }

  @Test
  void smtpSettingsRejectsInvalidPortAndFromAddress() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new SmtpSettings(true, "h", 0, MailTransport.NONE, null, null, null, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SmtpSettings(
                    true, "h", 65536, MailTransport.NONE, null, null, null, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SmtpSettings(
                    true, "h", 587, MailTransport.NONE, null, null, "no-at", null, null));
    assertThatNullPointerException()
        .isThrownBy(() -> new SmtpSettings(true, "h", 587, null, null, null, null, null, null));
  }

  @Test
  void smtpSettingsWithPasswordReplacesOnlyThePassword() {
    SmtpSettings s = smtp(true).withPassword("new-secret");
    assertThat(s.password()).isEqualTo("new-secret");
    assertThat(s.host()).isEqualTo("smtp.empresa.com");
  }
}
