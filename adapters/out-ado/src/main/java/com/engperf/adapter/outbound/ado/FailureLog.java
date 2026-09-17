package com.engperf.adapter.outbound.ado;

import com.engperf.application.ado.SourceFailure;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the sources a sync could not read.
 *
 * <p>A failure here is recorded and survived, not thrown: a repository that was renamed, removed or
 * lost permissions should cost only its own data. Before this, one stale registration aborted the
 * whole ingestion and the platform stopped measuring everything.
 */
final class FailureLog {

  private static final Logger LOG = LoggerFactory.getLogger(FailureLog.class);

  private final List<SourceFailure> failures = new ArrayList<>();

  /** Records the failure of {@code source}, keeping the message Azure DevOps itself reported. */
  void record(String source, RuntimeException cause) {
    String why = cause.getMessage();
    String reason = why == null || why.isBlank() ? cause.getClass().getSimpleName() : why;
    LOG.warn("ADO sync: {} falhou — {}", source, reason);
    failures.add(new SourceFailure(source, reason));
  }

  List<SourceFailure> all() {
    return List.copyOf(failures);
  }
}
