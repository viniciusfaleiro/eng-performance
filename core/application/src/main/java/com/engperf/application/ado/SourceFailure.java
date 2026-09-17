package com.engperf.application.ado;

import java.util.Objects;

/**
 * A source that could not be collected, and why.
 *
 * <p>{@code source} is the human-readable identifier the operator recognises — {@code repositório
 * org/projeto/repo} or {@code projeto org/projeto} — because the list exists to tell someone where
 * to act, not to be parsed.
 */
public record SourceFailure(String source, String reason) {

  public SourceFailure {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
  }
}
