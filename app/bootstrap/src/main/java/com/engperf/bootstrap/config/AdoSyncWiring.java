package com.engperf.bootstrap.config;

import com.engperf.application.ado.AdoStatsService;
import com.engperf.application.ado.AdoSyncService;
import com.engperf.application.port.inbound.AdoStatsUseCase;
import com.engperf.application.port.inbound.AdoSyncUseCase;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.AdoAuthPort;
import com.engperf.application.port.outbound.AdoEventSourcePort;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.SyncStatePort;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for the Azure DevOps sync. The device-code auth and REST source are
 * {@code @Component} adapters (discovered by scan); here we only assemble the application service
 * and give it a background thread and a real (wall-clock) time so backfill windows and last-sync
 * are live.
 */
@Configuration
public class AdoSyncWiring {

  @Bean
  Executor adoSyncExecutor() {
    return Executors.newSingleThreadExecutor(
        r -> {
          Thread t = new Thread(r, "ado-sync");
          t.setDaemon(true);
          return t;
        });
  }

  @Bean
  AdoSyncUseCase adoSyncUseCase(
      AdoAuthPort auth,
      AdoEventSourcePort source,
      EventStorePort store,
      SyncStatePort syncState,
      PlatformConfigUseCase config,
      Executor adoSyncExecutor) {
    return new AdoSyncService(
        auth, source, store, syncState, config, adoSyncExecutor, Clock.systemUTC());
  }

  @Bean
  AdoStatsUseCase adoStatsUseCase(
      StructureRepositoryPort structure, EventStorePort store, SyncStatePort syncState) {
    return new AdoStatsService(structure, store, syncState);
  }
}
