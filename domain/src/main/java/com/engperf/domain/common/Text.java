package com.engperf.domain.common;

import java.util.Objects;

/** Small shared helpers for validating text value objects across the domain. */
public final class Text {

  private Text() {}

  public static String required(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    String trimmed = value.strip();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }

  public static String optional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
