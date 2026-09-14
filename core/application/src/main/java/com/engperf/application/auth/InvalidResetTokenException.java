package com.engperf.application.auth;

/**
 * Thrown when a password reset token is unknown, expired or already consumed. Distinct from {@link
 * AuthenticationException} (401, session credentials) — an invalid reset token is bad input on a
 * public endpoint, mapped to HTTP 400. The message is deliberately generic: it must never let a
 * caller distinguish "never existed" from "expired" from "already used".
 */
public class InvalidResetTokenException extends RuntimeException {

  public InvalidResetTokenException(String message) {
    super(message);
  }
}
