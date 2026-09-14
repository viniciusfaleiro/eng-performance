package com.engperf.domain.config;

import com.engperf.domain.common.Text;
import java.util.Objects;

/**
 * Outgoing email configuration (singleton). The password is handled write-only by the layers above
 * this one — this record only validates the value it is given, it does not know whether the value
 * is encrypted, masked or omitted.
 *
 * @param appBaseUrl base URL used to build links in outgoing emails (e.g. the password-reset link)
 */
public record SmtpSettings(
    boolean enabled,
    String host,
    Integer port,
    MailTransport transport,
    String username,
    String password,
    String fromAddress,
    String fromName,
    String appBaseUrl) {

  public SmtpSettings {
    host = Text.optional(host);
    if (port != null && (port < 1 || port > 65535)) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
    Objects.requireNonNull(transport, "transport must not be null");
    username = Text.optional(username);
    password = Text.optional(password);
    fromAddress = Text.optional(fromAddress);
    if (fromAddress != null && !fromAddress.contains("@")) {
      throw new IllegalArgumentException("fromAddress must contain @");
    }
    fromName = Text.optional(fromName);
    appBaseUrl = Text.optional(appBaseUrl);
    if (appBaseUrl != null) {
      while (appBaseUrl.endsWith("/")) {
        appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      }
    }
  }

  /** Whether there is enough configuration to actually attempt an SMTP send. */
  public boolean isUsable() {
    return enabled && host != null && fromAddress != null;
  }

  /** Returns a copy with the password replaced; {@code null} keeps the current one unchanged. */
  public SmtpSettings withPassword(String newPassword) {
    return new SmtpSettings(
        enabled, host, port, transport, username, newPassword, fromAddress, fromName, appBaseUrl);
  }
}
