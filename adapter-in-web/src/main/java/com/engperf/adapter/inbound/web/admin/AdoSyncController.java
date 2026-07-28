package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.AdoSyncDtos.SyncStartDto;
import com.engperf.adapter.inbound.web.admin.AdoSyncDtos.SyncStatusDto;
import com.engperf.application.port.inbound.AdoSyncUseCase;
import com.engperf.domain.common.ConflictException;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-triggered Azure DevOps sync. Admin-only is enforced by {@code /api/admin/**} gating in the
 * auth filter. {@code POST} starts the interactive device-code job and returns the login prompt;
 * {@code GET .../status} reports progress.
 */
@RestController
public class AdoSyncController {

  private final AdoSyncUseCase sync;

  public AdoSyncController(AdoSyncUseCase sync) {
    this.sync = sync;
  }

  @PostMapping("/api/admin/ado/sync")
  public SyncStartDto start() {
    try {
      return SyncStartDto.from(sync.start());
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage()); // e.g. organization not connected yet
    }
  }

  @GetMapping("/api/admin/ado/sync/status")
  public SyncStatusDto status(@RequestParam String sessionId) {
    return sync.status(sessionId)
        .map(SyncStatusDto::from)
        .orElseThrow(() -> new NoSuchElementException("unknown sync session: " + sessionId));
  }
}
