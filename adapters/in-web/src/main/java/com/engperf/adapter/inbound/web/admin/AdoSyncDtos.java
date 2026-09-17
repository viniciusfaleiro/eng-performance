package com.engperf.adapter.inbound.web.admin;

import com.engperf.application.ado.SyncStatus;
import com.engperf.application.port.inbound.AdoSyncUseCase.Session;
import java.util.List;
import java.util.Map;

/** Response payloads for the admin-triggered Azure DevOps sync. */
public final class AdoSyncDtos {

  private AdoSyncDtos() {}

  /** What the Admin UI shows so the admin can complete the device-code login. */
  public record SyncStartDto(
      String sessionId, String userCode, String verificationUri, int intervalSeconds) {

    public static SyncStartDto from(Session s) {
      return new SyncStartDto(
          s.sessionId(),
          s.prompt().userCode(),
          s.prompt().verificationUri(),
          s.prompt().intervalSeconds());
    }
  }

  /** Uma fonte que não pôde ser lida — o que quem opera precisa corrigir. */
  public record FailureDto(String source, String reason) {}

  public record SyncStatusDto(
      String sessionId,
      String phase,
      Map<String, Integer> counts,
      boolean done,
      boolean failed,
      String message,
      String lastSyncedAt,
      List<FailureDto> failures) {

    public static SyncStatusDto from(SyncStatus s) {
      return new SyncStatusDto(
          s.sessionId(),
          s.phase(),
          s.counts(),
          s.done(),
          s.failed(),
          s.message(),
          s.lastSyncedAt() == null ? null : s.lastSyncedAt().toString(),
          s.failures().stream().map(f -> new FailureDto(f.source(), f.reason())).toList());
    }
  }
}
