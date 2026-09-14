package com.engperf.application.port.inbound;

import com.engperf.application.ado.DeviceCodePrompt;
import com.engperf.application.ado.RepositoryDiscoveryStatus;
import java.util.Optional;

/**
 * Admin-triggered repository discovery for one Azure DevOps (organization, project): authenticate
 * interactively, list its real repositories, and diff them against the registry. Mirrors {@link
 * AdoSyncUseCase}'s start/status shape so the UI can reuse the same device-code + polling pattern.
 */
public interface AdoDiscoveryUseCase {

  /**
   * Starts a discovery job; returns the session id and the device-code prompt to show the admin.
   */
  Session start(String organization, String project);

  Optional<RepositoryDiscoveryStatus> status(String sessionId);

  /**
   * Applies a finished, not-yet-applied diff: registers every repository to insert (unmapped, with
   * its suggested production stage if any) and removes every repository no longer present in Azure
   * DevOps. A no-op if the session was already applied.
   *
   * @throws IllegalStateException if the session is unknown or its diff has not finished yet
   */
  void apply(String sessionId);

  record Session(String sessionId, DeviceCodePrompt prompt) {}
}
