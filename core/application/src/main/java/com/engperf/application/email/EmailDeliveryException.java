package com.engperf.application.email;

/** Thrown when an email could not be delivered (e.g. the configured SMTP server rejected it). */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }

  public EmailDeliveryException(String message) {
    super(message);
  }
}
