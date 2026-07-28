package com.engperf.application.ado;

/** Callback the event source uses to report progress (phase + per-source running counts). */
@FunctionalInterface
public interface ProgressReporter {
  void update(String phase, String source, int count);
}
