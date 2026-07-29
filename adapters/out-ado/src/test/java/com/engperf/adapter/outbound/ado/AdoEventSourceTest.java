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
  void chunksWorkItemQueryByMonthAndBatchesIdsUnderTheLimit() {
    FakeStructure structure =
        new FakeStructure(List.of(new Repository("repoA", "orgX", "ProjP", "t:1", "Production")));
    PagingClient client = new PagingClient();
    AdoEventSource source = new AdoEventSource(client, new FakeConfig(), structure);

    List<RawEvent> events =
        source.fetchSince("tok", Instant.parse("2026-01-01T00:00:00Z"), (phase, s, c) -> {});

    // WIQL is queried month-by-month (several calls) with date-only bounds (no time component).
    long wiqlCalls = client.urls.stream().filter(u -> u.contains("/wit/wiql")).count();
    assertThat(wiqlCalls).isGreaterThan(1);
    assertThat(client.wiqlBodies).allMatch(b -> !b.contains("T00:00:00"));

    // 250 distinct ids → batch GETs of at most 200 ids each, covering all of them exactly once.
    assertThat(client.batchSizes).isNotEmpty().allMatch(size -> size <= 200);
    assertThat(client.batchSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(250);
    assertThat(events.stream().filter(e -> e.type() == EventType.WORKITEM).count()).isEqualTo(250);
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
      if (url.contains("/build/builds") && url.contains("orgX")) {
        return json(
            "{\"value\":["
                + "{\"id\":1,\"result\":\"succeeded\",\"stageName\":\"Production\","
                + "\"queueTime\":\"2026-06-10T10:00:00Z\",\"finishTime\":\"2026-06-10T10:30:00Z\","
                + "\"repository\":{\"name\":\"repoA\"}},"
                + "{\"id\":2,\"result\":\"succeeded\",\"stageName\":\"Production\","
                + "\"queueTime\":\"2026-06-10T10:00:00Z\",\"finishTime\":\"2026-06-10T10:30:00Z\","
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

  /** Returns 250 work-item ids for every WIQL chunk and echoes back items for each batched GET. */
  private static final class PagingClient implements AdoRestClient {
    final List<String> urls = new ArrayList<>();
    final List<String> wiqlBodies = new ArrayList<>();
    final List<Integer> batchSizes = new ArrayList<>();

    @Override
    public JsonNode get(String url, String token) {
      urls.add(url);
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
      urls.add(url);
      wiqlBodies.add(body);
      StringJoiner ids = new StringJoiner(",", "{\"workItems\":[", "]}");
      for (int i = 1; i <= 250; i++) {
        ids.add("{\"id\":" + i + "}");
      }
      return json(ids.toString());
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
