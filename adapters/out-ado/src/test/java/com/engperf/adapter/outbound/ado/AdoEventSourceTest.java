package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;

/**
 * The source iterates the registered repos across orgs and attributes deploys by their source repo.
 */
class AdoEventSourceTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void iteratesRegisteredReposAcrossOrgsAndAttributesDeployBySourceRepo() {
    FakeStructure structure =
        new FakeStructure(
            List.of(
                new Repository("repoA", "orgX", "ProjP", "t:1", "Production"),
                new Repository("repoB", "orgY", "ProjQ", "t:2", "Production")));
    FakeClient client = new FakeClient();
    AdoEventSource source = new AdoEventSource(client, new FakeConfig(), structure);

    List<RawEvent> events =
        source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {});

    // Multi-org: each repo is fetched from its own organization/project.
    assertThat(client.urls)
        .anyMatch(u -> u.contains("dev.azure.com/orgX/ProjP/_apis/git/repositories/repoA"));
    assertThat(client.urls)
        .anyMatch(u -> u.contains("dev.azure.com/orgY/ProjQ/_apis/git/repositories/repoB"));

    // A build whose source repo is registered → a deploy for that repo; the unregistered one
    // skipped.
    List<RawEvent> deploys = events.stream().filter(e -> e.type() == EventType.DEPLOY).toList();
    assertThat(deploys).extracting(RawEvent::repoKey).containsExactly("repoA");
  }

  @Test
  void bisectsWorkItemWindowsThatExceedTheWiqlLimitAndBatchesIds() {
    FakeStructure structure =
        new FakeStructure(List.of(new Repository("repoA", "orgX", "ProjP", "t:1", "Production")));
    BisectingClient client = new BisectingClient();
    AdoEventSource source = new AdoEventSource(client, new FakeConfig(), structure);
    LocalDate start = LocalDate.parse("2026-01-01");
    long expectedDays =
        ChronoUnit.DAYS.between(start, LocalDate.now(ZoneOffset.UTC).plusDays(1)); // one id/day

    List<RawEvent> events =
        source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {});

    // The over-limit windows were split until each accepted window fit under the cap (no VS402337
    // propagated — the sync completed).
    assertThat(client.acceptedSpanDays).isNotEmpty().allMatch(days -> days <= BisectingClient.CAP);
    // Every day's work item was collected exactly once (bisection is lossless), then batched ≤200.
    assertThat(events.stream().filter(e -> e.type() == EventType.WORKITEM).count())
        .isEqualTo(expectedDays);
    assertThat(client.batchSizes).isNotEmpty().allMatch(size -> size <= 200);
  }

  private static JsonNode json(String s) {
    try {
      return JSON.readTree(s);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final class FakeClient implements AdoRestClient {
    final List<String> urls = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
      if (url.contains("/timeline")) {
        // The production stage lives in the build Timeline, not the Build object.
        return json(
            "{\"records\":[{\"id\":\"st\",\"type\":\"Stage\",\"name\":\"Production\","
                + "\"result\":\"succeeded\",\"finishTime\":\"2026-06-10T10:30:00Z\"}]}");
      }
      if (url.contains("/build/builds") && url.contains("orgX")) {
        return json(
            "{\"value\":["
                + "{\"id\":1,\"queueTime\":\"2026-06-10T10:00:00Z\","
                + "\"repository\":{\"name\":\"repoA\"}},"
                + "{\"id\":2,\"queueTime\":\"2026-06-10T10:00:00Z\","
                + "\"repository\":{\"name\":\"ghost\"}}]}");
      }
      return json("{\"value\":[]}");
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      urls.add(url);
      return json("{\"workItems\":[]}");
    }
  }

  /**
   * Models ADO's WIQL cap: a window wider than {@link #CAP} days is refused with VS402337; a
   * fitting window returns one id per day (its epoch-day), so bisection converges and coverage is
   * lossless. The batched GET echoes a work item per requested id.
   */
  private static final class BisectingClient implements AdoRestClient {
    static final long CAP = 45; // days a single WIQL window may span before ADO refuses it
    final List<Long> acceptedSpanDays = new ArrayList<>();
    final List<Integer> batchSizes = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      if (!url.contains("/wit/workitems?ids=")) {
        return json("{\"value\":[]}"); // no PRs, commits or builds in this scenario
      }
      String idsParam = url.substring(url.indexOf("ids=") + 4, url.indexOf("&fields="));
      String[] ids = idsParam.split(",");
      batchSizes.add(ids.length);
      StringJoiner items = new StringJoiner(",", "{\"value\":[", "]}");
      for (String id : ids) {
        items.add(
            "{\"id\":"
                + id
                + ",\"fields\":{\"System.WorkItemType\":\"Bug\","
                + "\"System.ChangedDate\":\"2026-03-01T10:00:00Z\"}}");
      }
      return json(items.toString());
    }

    @Override
    public JsonNode post(String url, String token, String body) {
      LocalDate from = LocalDate.parse(between(body, ">= '", "'"));
      LocalDate to = LocalDate.parse(between(body, "< '", "'"));
      long span = ChronoUnit.DAYS.between(from, to);
      if (span > CAP) {
        throw new IllegalStateException(
            "Azure DevOps wiql -> HTTP 400 — VS402337: The number of work items returned exceeds the"
                + " size limit of 20000.");
      }
      acceptedSpanDays.add(span);
      StringJoiner ids = new StringJoiner(",", "{\"workItems\":[", "]}");
      for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
        ids.add("{\"id\":" + d.toEpochDay() + "}"); // one distinct id per day
      }
      return json(ids.toString());
    }

    private static String between(String s, String open, String close) {
      int a = s.indexOf(open) + open.length();
      return s.substring(a, s.indexOf(close, a));
    }
  }

  private static final class FakeConfig implements PlatformConfigUseCase {
    @Override
    public AdoIntegration adoIntegration() {
      return new AdoIntegration(true, null);
    }

    @Override
    public AdoIntegration markAdoConnected() {
      return adoIntegration();
    }

    @Override
    public AiConvention aiConvention() {
      return new AiConvention(AiStrategy.TRAILER, "Co-authored-by: Copilot", null, null, false);
    }

    @Override
    public AiConvention saveAiConvention(
        AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive) {
      return aiConvention();
    }
  }

  private static final class FakeStructure implements StructureRepositoryPort {
    private final List<Repository> repos;

    FakeStructure(List<Repository> repos) {
      this.repos = repos;
    }

    @Override
    public List<Repository> findRepositories() {
      return repos;
    }

    @Override
    public Vertical saveVertical(Vertical v) {
      return v;
    }

    @Override
    public List<Vertical> findVerticals() {
      return List.of();
    }

    @Override
    public Optional<Vertical> findVertical(String id) {
      return Optional.empty();
    }

    @Override
    public void deleteVertical(String id) {}

    @Override
    public Team saveTeam(Team t) {
      return t;
    }

    @Override
    public List<Team> findTeams() {
      return List.of();
    }

    @Override
    public Optional<Team> findTeam(String id) {
      return Optional.empty();
    }

    @Override
    public void deleteTeam(String id) {}

    @Override
    public Person savePerson(Person p) {
      return p;
    }

    @Override
    public List<Person> findPeople() {
      return List.of();
    }

    @Override
    public Optional<Person> findPerson(String id) {
      return Optional.empty();
    }

    @Override
    public void deletePerson(String id) {}

    @Override
    public Repository saveRepository(Repository r) {
      return r;
    }

    @Override
    public Optional<Repository> findRepository(String key) {
      return repos.stream().filter(r -> r.key().equals(key)).findFirst();
    }

    @Override
    public void deleteRepository(String key) {}

    @Override
    public CommitterIdentity saveIdentity(CommitterIdentity c) {
      return c;
    }

    @Override
    public List<CommitterIdentity> findIdentities() {
      return List.of();
    }

    @Override
    public Optional<CommitterIdentity> findIdentity(String identity) {
      return Optional.empty();
    }
  }
}
