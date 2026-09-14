package com.engperf.adapter.outbound.email;

import com.engperf.application.email.EmailDeliveryException;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Sends a message through a real SMTP server described by {@link SmtpSettings}. Built fresh per
 * send from the currently saved settings — never cached — so a change made in the Admin screen
 * takes effect on the very next send, with no restart.
 */
final class SmtpEmailSender {

  private static final int TIMEOUT_MILLIS = 5000;

  private final SmtpSettings settings;

  SmtpEmailSender(SmtpSettings settings) {
    this.settings = settings;
  }

  void send(String to, String subject, String body) {
    JavaMailSenderImpl mailSender = buildSender();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    message.setFrom(fromHeader());
    try {
      mailSender.send(message);
    } catch (MailException e) {
      throw new EmailDeliveryException("failed to send email via SMTP: " + e.getMessage(), e);
    }
  }

  private String fromHeader() {
    return settings.fromName() == null
        ? settings.fromAddress()
        : settings.fromName() + " <" + settings.fromAddress() + ">";
  }

  private JavaMailSenderImpl buildSender() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(settings.host());
    if (settings.port() != null) {
      sender.setPort(settings.port());
    }
    if (settings.username() != null) {
      sender.setUsername(settings.username());
    }
    if (settings.password() != null) {
      sender.setPassword(settings.password());
    }

    Properties props = sender.getJavaMailProperties();
    props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
    props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MILLIS));
    props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MILLIS));
    props.put("mail.smtp.auth", String.valueOf(settings.username() != null));
    if (settings.transport() == MailTransport.STARTTLS) {
      props.put("mail.smtp.starttls.enable", "true");
    } else if (settings.transport() == MailTransport.SSL) {
      props.put("mail.smtp.ssl.enable", "true");
    }
    return sender;
  }
}
