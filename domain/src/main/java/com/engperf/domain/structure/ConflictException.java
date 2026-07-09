package com.engperf.domain.structure;

/**
 * Thrown when an operation violates a structural invariant that is a conflict rather than a bad
 * input — e.g. more than one open membership, or reassigning a repository that already belongs to a
 * team. Mapped to HTTP 409 by the inbound web adapter.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
