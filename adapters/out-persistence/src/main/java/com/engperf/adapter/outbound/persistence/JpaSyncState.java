package com.engperf.adapter.outbound.persistence;

import com.engperf.application.ado.SyncState;
import com.engperf.application.port.outbound.SyncStatePort;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed {@link SyncStatePort}: a single row holds the ADO sync cursor. */
@Component
@Transactional
public class JpaSyncState implements SyncStatePort {

  private static final String ID = "ado";

  private final SyncStateJpaRepository rows;

  public JpaSyncState(SyncStateJpaRepository rows) {
    this.rows = rows;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SyncState> load() {
    return rows.findById(ID)
        .map(e -> new SyncState(e.getWatermark(), e.getLastSyncedAt(), e.getEventCount()));
  }

  @Override
  public void save(SyncState state) {
    rows.save(new SyncStateEntity(ID, state.watermark(), state.lastSyncedAt(), state.eventCount()));
  }
}
