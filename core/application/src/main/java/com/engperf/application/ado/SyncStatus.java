package com.engperf.application.ado;

import java.time.Instant;
import java.util.Map;

/**
 * Live status of an in-flight or finished sync job: current phase, per-source counts, terminal
 * flags and, when finished, the recorded last-sync time.
 */
public record SyncStatus(
    String sessionId,
    String phase,
    Map<String, Integer> counts,
    boolean done,
    boolean failed,
    String message,
    Instant lastSyncedAt) {

  public SyncStatus {
    counts = Map.copyOf(counts);
  }
}
