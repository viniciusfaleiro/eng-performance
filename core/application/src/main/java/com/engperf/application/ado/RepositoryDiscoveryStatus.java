package com.engperf.application.ado;

import java.util.List;

/**
 * Live status of a repository-discovery job. Once {@code done} (and not {@code failed}), {@code
 * toInsert}/{@code toRemove} hold the diff — nothing is applied to the registry until {@link
 * com.engperf.application.port.inbound.AdoDiscoveryUseCase#apply} is called; {@code applied} tracks
 * whether that has already happened, so applying twice is a no-op rather than a double mutation.
 */
public record RepositoryDiscoveryStatus(
    String sessionId,
    String phase,
    boolean done,
    boolean failed,
    String message,
    List<DiscoveredRepository> toInsert,
    List<String> toRemove,
    boolean applied) {

  public RepositoryDiscoveryStatus {
    toInsert = List.copyOf(toInsert);
    toRemove = List.copyOf(toRemove);
  }
}
