package com.engperf.domain.structure;

import java.util.Objects;

/** Small internal helpers for validating structure value objects. */
final class Validation {

  private Validation() {}

  static String text(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    String trimmed = value.strip();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }

  static String optional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
