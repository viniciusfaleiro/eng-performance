package com.engperf.application.auth;

/**
 * Thrown when credentials are invalid or a session token is missing/invalid. Mapped to HTTP 401.
 */
public class AuthenticationException extends RuntimeException {

  public AuthenticationException(String message) {
    super(message);
  }
}
