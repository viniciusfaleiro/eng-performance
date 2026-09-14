package com.engperf.adapter.inbound.web.admin;

import com.engperf.application.ado.DiscoveredRepository;
import com.engperf.application.ado.RepositoryDiscoveryStatus;
import com.engperf.application.port.inbound.AdoDiscoveryUseCase.Session;
import java.util.List;

/** Request/response payloads for the admin-triggered repository discovery. */
final class AdoDiscoveryDtos {

  private AdoDiscoveryDtos() {}

  record DiscoverRequest(String organization, String project) {}

  /** What the Admin UI shows so the admin can complete the device-code login. */
  record DiscoveryStartDto(
      String sessionId, String userCode, String verificationUri, int intervalSeconds) {

    static DiscoveryStartDto from(Session s) {
      return new DiscoveryStartDto(
          s.sessionId(),
          s.prompt().userCode(),
          s.prompt().verificationUri(),
          s.prompt().intervalSeconds());
    }
  }

  record DiscoveredRepositoryDto(String key, String suggestedProductionStage) {

    static DiscoveredRepositoryDto from(DiscoveredRepository r) {
      return new DiscoveredRepositoryDto(r.key(), r.suggestedProductionStage());
    }
  }

  record DiscoveryStatusDto(
      String sessionId,
      String phase,
      boolean done,
      boolean failed,
      String message,
      List<DiscoveredRepositoryDto> toInsert,
      List<String> toRemove,
      boolean applied) {

    static DiscoveryStatusDto from(RepositoryDiscoveryStatus s) {
      return new DiscoveryStatusDto(
          s.sessionId(),
          s.phase(),
          s.done(),
          s.failed(),
          s.message(),
          s.toInsert().stream().map(DiscoveredRepositoryDto::from).toList(),
          s.toRemove(),
          s.applied());
    }
  }
}
