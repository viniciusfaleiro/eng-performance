package com.engperf.adapter.outbound.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailSenderTest {

  private static JavaMailSenderImpl build(SmtpSettings settings) throws Exception {
    SmtpEmailSender sender = new SmtpEmailSender(settings);
    Method m = SmtpEmailSender.class.getDeclaredMethod("buildSender");
    m.setAccessible(true);
    return (JavaMailSenderImpl) m.invoke(sender);
  }

  @Test
  void mapsHostPortCredentialsAndStartTls() throws Exception {
    JavaMailSenderImpl mail =
        build(
            new SmtpSettings(
                true,
                "smtp.empresa.com",
                587,
                MailTransport.STARTTLS,
                "no-reply",
                "s3cret",
                "no-reply@empresa.com",
                "Eng Performance",
                "https://empresa.com"));

    assertThat(mail.getHost()).isEqualTo("smtp.empresa.com");
    assertThat(mail.getPort()).isEqualTo(587);
    assertThat(mail.getUsername()).isEqualTo("no-reply");
    assertThat(mail.getPassword()).isEqualTo("s3cret");
    assertThat(mail.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"))
        .isEqualTo("true");
    assertThat(mail.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
  }

  @Test
  void mapsSslAndSkipsCredentialsWhenAbsent() throws Exception {
    JavaMailSenderImpl mail =
        build(
            new SmtpSettings(
                true,
                "smtp.empresa.com",
                465,
                MailTransport.SSL,
                null,
                null,
                "no-reply@empresa.com",
                null,
                null));

    assertThat(mail.getUsername()).isNull();
    assertThat(mail.getJavaMailProperties().getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
    assertThat(mail.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
  }
}
