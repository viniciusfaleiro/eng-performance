package com.engperf.domain.config;

import com.engperf.domain.common.Text;
import java.util.Objects;

/** Convention that marks a commit as AI-assisted (MVP: commit trailer/tag, no external API). */
public record AiConvention(
    AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive) {

  public AiConvention {
    Objects.requireNonNull(strategy, "strategy must not be null");
    trailer = Text.optional(trailer);
    tag = Text.optional(tag);
    regex = Text.optional(regex);
  }
}
