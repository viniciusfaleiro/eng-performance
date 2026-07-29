package com.engperf.application.port.inbound;

import com.engperf.application.ado.DeviceCodePrompt;
import com.engperf.application.ado.SyncStatus;
import java.util.Optional;

/**
 * Admin-triggered Azure DevOps sync. {@link #start(boolean)} begins the interactive device-code
 * flow and launches the load as a background job; {@link #status(String)} reports its progress.
 */
public interface AdoSyncUseCase {

  /**
   * Starts a sync job; returns the session id and the device-code prompt to show the admin. When
   * {@code fullBackfill} is true, the load ignores the watermark and re-ingests the whole backfill
   * window (idempotent upsert), reprocessing existing items with the current mapping.
   */
  Session start(boolean fullBackfill);

  Optional<SyncStatus> status(String sessionId);

  record Session(String sessionId, DeviceCodePrompt prompt) {}
}
