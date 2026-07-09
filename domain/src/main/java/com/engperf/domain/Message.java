package com.engperf.domain;

import java.util.Objects;

/**
 * A validated text message — the single domain concept of the echo slice.
 *
 * <p>Trivial on purpose: it exists to give the harness a real (testable) domain invariant while the
 * production domain is designed later.
 */
public record Message(String text) {

  private static final int MAX_LENGTH = 280;

  public Message {
    Objects.requireNonNull(text, "text must not be null");
    String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    if (trimmed.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("message must be at most " + MAX_LENGTH + " characters");
    }
    text = trimmed;
  }

  public static Message of(String text) {
    return new Message(text);
  }
}
