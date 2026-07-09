package com.engperf.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageTest {

  @Test
  void trimsSurroundingWhitespace() {
    assertThat(Message.of("  hi  ").text()).isEqualTo("hi");
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> Message.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsBlank() {
    assertThatThrownBy(() -> Message.of("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsTooLong() {
    String tooLong = "x".repeat(281);
    assertThatThrownBy(() -> Message.of(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("280");
  }
}
