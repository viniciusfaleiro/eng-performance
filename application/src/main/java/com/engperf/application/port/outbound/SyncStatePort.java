package com.engperf.application.port.outbound;

import com.engperf.application.ado.SyncState;
import java.util.Optional;

/** Persists the Azure DevOps sync cursor (watermark + last-run summary). */
public interface SyncStatePort {

  Optional<SyncState> load();

  void save(SyncState state);
}
