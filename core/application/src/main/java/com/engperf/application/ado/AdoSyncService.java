package com.engperf.application.ado;

import com.engperf.application.port.inbound.AdoSyncUseCase;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.AdoAuthPort;
import com.engperf.application.port.outbound.AdoEventSourcePort;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.SyncStatePort;
import com.engperf.domain.metrics.RawEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Orchestrates an admin-triggered Azure DevOps sync as a background job: begin device-code auth →
 * poll for the user's token → fetch activity after the watermark → persist (idempotent) → advance
 * the watermark → record a summary. Job state lives in memory and is read back via {@link #status}.
 * The load itself runs on the injected {@link Executor}; nothing but the short-lived token is held.
 */
public final class AdoSyncService implements AdoSyncUseCase {

  private static final int BACKFILL_MONTHS = 6;

  private final AdoAuthPort auth;
  private final AdoEventSourcePort source;
  private final EventStorePort store;
  private final SyncStatePort syncState;
  private final PlatformConfigUseCase config;
  private final Executor executor;
  private final Clock clock;

  private final Map<String, Job> jobs = new ConcurrentHashMap<>();

  public AdoSyncService(
      AdoAuthPort auth,
      AdoEventSourcePort source,
      EventStorePort store,
      SyncStatePort syncState,
      PlatformConfigUseCase config,
      Executor executor,
      Clock clock) {
    this.auth = Objects.requireNonNull(auth, "auth must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.store = Objects.requireNonNull(store, "store must not be null");
    this.syncState = Objects.requireNonNull(syncState, "syncState must not be null");
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Session start() {
    // No org to configure: ingestion iterates the registered repositories (each with its own org).
    DeviceCodePrompt prompt = auth.beginDeviceCode();
    Job job = new Job();
    String sessionId = UUID.randomUUID().toString();
    jobs.put(sessionId, job);
    executor.execute(() -> run(job, prompt));
    return new Session(sessionId, prompt);
  }

  @Override
  public Optional<SyncStatus> status(String sessionId) {
    return Optional.ofNullable(jobs.get(sessionId)).map(j -> j.snapshot(sessionId));
  }

  private void run(Job job, DeviceCodePrompt prompt) {
    try {
      String token = awaitToken(job, prompt);
      job.phase = "syncing";
      Instant since = watermarkOrBackfill();
      List<RawEvent> events = source.fetchSince(token, since, job::report);
      store.saveAll(events);
      Instant now = clock.instant();
      Instant watermark =
          events.stream().map(RawEvent::occurredAt).max(Comparator.naturalOrder()).orElse(since);
      syncState.save(new SyncState(watermark, now, events.size()));
      config.markAdoConnected(); // real ingestion is live → the dev seeder stands down
      job.finish(now, events.size());
    } catch (AdoAuthException e) {
      job.fail("login não concluído: " + e.getMessage());
    } catch (RuntimeException e) {
      job.fail("falha na sincronização: " + e.getMessage());
    }
  }

  private String awaitToken(Job job, DeviceCodePrompt prompt) {
    Instant deadline = clock.instant().plusSeconds(prompt.expiresInSeconds());
    while (true) {
      Optional<String> token = auth.poll(prompt.deviceCode());
      if (token.isPresent()) {
        return token.get();
      }
      if (clock.instant().isAfter(deadline)) {
        throw new AdoAuthException("device code expired");
      }
      sleep(prompt.intervalSeconds());
    }
  }

  private Instant watermarkOrBackfill() {
    return syncState
        .load()
        .map(SyncState::watermark)
        .orElseGet(() -> clock.instant().minusSeconds((long) BACKFILL_MONTHS * 30 * 24 * 3600));
  }

  private static void sleep(int seconds) {
    try {
      Thread.sleep(Math.max(1, seconds) * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AdoAuthException("interrupted while waiting for login");
    }
  }

  /** Mutable in-memory job state; snapshotted into an immutable {@link SyncStatus} on read. */
  private static final class Job {
    private volatile String phase = "awaiting-login";
    private volatile boolean done;
    private volatile boolean failed;
    private volatile String message = "";
    private volatile Instant lastSyncedAt;
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();

    void report(String phase, String source, int count) {
      this.phase = phase;
      counts.put(source, count);
    }

    void finish(Instant at, int total) {
      phase = "done";
      done = true;
      lastSyncedAt = at;
      message = total + " eventos sincronizados";
    }

    void fail(String why) {
      phase = "failed";
      failed = true;
      done = true;
      message = why;
    }

    SyncStatus snapshot(String sessionId) {
      return new SyncStatus(sessionId, phase, counts, done, failed, message, lastSyncedAt);
    }
  }
}
