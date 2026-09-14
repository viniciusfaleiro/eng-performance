package com.engperf.adapter.outbound.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback used when no SMTP server is configured (or enabled): writes the message to the
 * application log instead of delivering it, so flows that depend on email stay exercisable in
 * dev/local without a mail server.
 */
final class LogEmailSender {

  private static final Logger LOG = LoggerFactory.getLogger(LogEmailSender.class);

  void send(String to, String subject, String body) {
    LOG.info("Email (log fallback — no SMTP configured) to={} subject={}\n{}", to, subject, body);
  }
}
