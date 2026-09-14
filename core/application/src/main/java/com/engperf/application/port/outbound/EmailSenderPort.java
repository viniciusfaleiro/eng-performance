package com.engperf.application.port.outbound;

import com.engperf.application.email.EmailDeliveryException;

/**
 * Outbound port: send a transactional email. Routing (SMTP vs. log fallback) is an adapter concern.
 */
public interface EmailSenderPort {

  /**
   * Sends {@code body} to {@code to} with {@code subject}.
   *
   * @throws EmailDeliveryException if the message could not be delivered
   */
  void send(String to, String subject, String body);
}
