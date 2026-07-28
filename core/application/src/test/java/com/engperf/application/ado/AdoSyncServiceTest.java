package com.engperf.application.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.inbound.AdoSyncUseCase.Session;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.AdoAuthPort;
import com.engperf.application.port.outbound.AdoEventSourcePort;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.SyncStatePort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdoSyncServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeAuth auth = new FakeAuth();
  private final FakeSource source = new FakeSource();
  private final FakeStore store = new FakeStore();
  private final FakeSyncState syncState = new FakeSyncState();
  private final FakeConfig config = new FakeConfig();
  private final AdoSyncService service =
      new AdoSyncService(auth, source, store, syncState, config, Runnable::run, CLOCK);

  @Test
  void firstRunBackfillsThenSecondRunFetchesTheDiff() {
    source.events =
        List.of(commit("c1", "2026-06-20T10:00:00Z"), commit("c2", "2026-06-25T10:00:00Z"));

    Session first = service.start();
    var status1 = service.status(first.sessionId()).orElseThrow();
    assertThat(status1.done()).isTrue();
    assertThat(status1.failed()).isFalse();
    // First run: since = ~6 months before the fixed clock (a backfill window in the past).
    assertThat(source.lastSince).isBefore(Instant.parse("2026-02-01T00:00:00Z"));
    assertThat(store.byId).containsKeys("c1", "c2");
    assertThat(syncState.state.watermark()).isEqualTo(Instant.parse("2026-06-25T10:00:00Z"));

    // Second run: since = the recorded watermark (only the diff).
    service.start();
    assertThat(source.lastSince).isEqualTo(Instant.parse("2026-06-25T10:00:00Z"));
  }

  @Test
  void reRunningDoesNotDuplicate() {
    source.events = List.of(commit("c1", "2026-06-20T10:00:00Z"));
    service.start();
    service.start();
    assertThat(store.byId).hasSize(1); // upsert by id — no duplicates
  }

  @Test
  void awaitsLoginBeforeSyncing() {
    auth.pollsUntilToken = 2; // pending once, then the token
    source.events = List.of(commit("c1", "2026-06-20T10:00:00Z"));
    Session s = service.start();
    assertThat(service.status(s.sessionId()).orElseThrow().done()).isTrue();
    assertThat(auth.pollCount).isEqualTo(2);
  }

  private static RawEvent commit(String id, String date) {
    return new RawEvent(
        id, EventType.COMMIT, Instant.parse(date), null, "ana@x", null, null, false, Map.of());
  }

  private static final class FakeAuth implements AdoAuthPort {
    int pollsUntilToken = 1;
    int pollCount;

    @Override
    public DeviceCodePrompt beginDeviceCode() {
      return new DeviceCodePrompt("ABC-123", "https://microsoft.com/devicelogin", "dev", 0, 900);
    }

    @Override
    public Optional<String> poll(String deviceCode) {
      pollCount++;
      return pollCount >= pollsUntilToken ? Optional.of("token") : Optional.empty();
    }
  }

  private static final class FakeSource implements AdoEventSourcePort {
    List<RawEvent> events = List.of();
    Instant lastSince;

    @Override
    public List<RawEvent> fetchSince(
        String token, String org, String stage, Instant since, ProgressReporter progress) {
      lastSince = since;
      progress.update("syncing", "commits", events.size());
      return events;
    }
  }

  private static final class FakeStore implements EventStorePort {
    final Map<String, RawEvent> byId = new LinkedHashMap<>();

    @Override
    public void saveAll(Collection<RawEvent> events) {
      events.forEach(e -> byId.put(e.id(), e));
    }

    @Override
    public List<RawEvent> findByTypeBetween(EventType type, Instant from, Instant to) {
      return new ArrayList<>(byId.values());
    }

    @Override
    public long count() {
      return byId.size();
    }
  }

  private static final class FakeSyncState implements SyncStatePort {
    SyncState state;

    @Override
    public Optional<SyncState> load() {
      return Optional.ofNullable(state);
    }

    @Override
    public void save(SyncState s) {
      state = s;
    }
  }

  private static final class FakeConfig implements PlatformConfigUseCase {
    @Override
    public AdoIntegration adoIntegration() {
      return new AdoIntegration("https://dev.azure.com/org", null, "Production", true, null);
    }

    @Override
    public AdoIntegration saveAdoIntegration(String url, String pat, String rule) {
      return adoIntegration();
    }

    @Override
    public AdoIntegration testAdoConnection() {
      return adoIntegration();
    }

    @Override
    public AiConvention aiConvention() {
      return new AiConvention(AiStrategy.TRAILER, "Co-authored-by: Copilot", "[ai]", null, false);
    }

    @Override
    public AiConvention saveAiConvention(
        AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive) {
      return aiConvention();
    }
  }
}
