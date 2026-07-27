package com.engperf.adapter.inbound.web.auth;

/** Thrown when an authenticated user requests a node outside their access scope. Mapped to 403. */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
