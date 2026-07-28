package com.engperf.domain.common;

/**
 * Thrown when an operation violates an invariant that is a conflict rather than bad input — e.g. a
 * duplicate email or more than one open membership. Mapped to HTTP 409 by the inbound web adapter.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
