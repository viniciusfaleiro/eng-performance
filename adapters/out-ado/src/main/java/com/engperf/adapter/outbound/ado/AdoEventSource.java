package com.engperf.adapter.outbound.ado;

import com.engperf.application.ado.ProgressReporter;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.AdoEventSourcePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Fetches Azure DevOps activity (api-version 7.1) for the **registered repositories across any
 * organizations** — no single configured org. Per repo it fetches PRs and commits; per distinct
 * {@code (organization, project)} it fetches that project's pipeline runs (attributed to their
 * source repo and classified by that repo's production-stage rule) and work items. Mapping to
 * {@link RawEvent} is done by {@link AdoMapper}; the REST paths are verified against a real org
 * during acceptance.
 */
@Component
public final class AdoEventSource implements AdoEventSourcePort {

  private static final String API = "api-version=7.1";

  private final AdoRestClient client;
  private final PlatformConfigUseCase config;
  private final StructureRepositoryPort structure;

  AdoEventSource(
      AdoRestClient client, PlatformConfigUseCase config, StructureRepositoryPort structure) {
    this.client = client;
    this.config = config;
    this.structure = structure;
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AdoEventSource(PlatformConfigUseCase config, StructureRepositoryPort structure) {
    this(new HttpAdoRestClient(), config, structure);
  }

  @Override
  public List<RawEvent> fetchSince(String token, Instant since, ProgressReporter progress) {
    String sinceIso = since.toString();
    Predicate<String> isAi = aiDetector(config.aiConvention());
    List<Repository> repos = structure.findRepositories();
    List<RawEvent> events = new ArrayList<>();
    int prs = 0;
    int commits = 0;

    // Per repository: PRs + commits (using the repo's own organization/project/key).
    for (Repository repo : repos) {
      String base =
          normOrg(repo.organization())
              + "/"
              + enc(repo.project())
              + "/_apis/git/repositories/"
              + enc(repo.key());
      for (JsonNode pr :
          arr(
              client.get(
                  base + "/pullrequests?searchCriteria.status=completed&$top=200&" + API, token))) {
        if (before(pr.path("closedDate").asText(""), since)) {
          continue;
        }
        events.add(AdoMapper.pullRequest(pr));
        events.addAll(AdoMapper.reviews(pr));
        prs++;
      }
      progress.update("syncing", "prs", prs);
      for (JsonNode c :
          arr(
              client.get(
                  base + "/commits?searchCriteria.fromDate=" + enc(sinceIso) + "&$top=1000&" + API,
                  token))) {
        events.add(AdoMapper.commit(c, repo.key(), isAi));
        commits++;
      }
      progress.update("syncing", "commits", commits);
    }

    // Per distinct (org, project): pipeline runs (deploys) + work items — project-scoped in ADO.
    int deploys = 0;
    int workItems = 0;
    Set<String> seenProjects = new HashSet<>();
    for (Repository repo : repos) {
      if (!seenProjects.add(repo.organization() + "|" + repo.project())) {
        continue;
      }
      String org = normOrg(repo.organization());
      String proj = enc(repo.project());
      Map<String, String> stageBySourceRepo = productionStages(repos, repo);
      for (JsonNode run :
          arr(
              client.get(
                  org + "/" + proj + "/_apis/build/builds?minTime=" + enc(sinceIso) + "&" + API,
                  token))) {
        String sourceRepo = run.path("repository").path("name").asText("");
        String stage = stageBySourceRepo.get(sourceRepo);
        if (stage == null) {
          continue; // a run whose source repository is not registered → skipped
        }
        AdoMapper.deploy(run, stage).ifPresent(events::add);
        deploys++;
      }
      progress.update("syncing", "deploys", deploys);
      workItems += fetchWorkItems(org, proj, sinceIso, token, events);
      progress.update("syncing", "workitems", workItems);
    }
    return events;
  }

  /**
   * The production-stage rule for each registered repo in the same (org, project) as {@code any}.
   */
  private static Map<String, String> productionStages(List<Repository> repos, Repository any) {
    Map<String, String> byRepo = new HashMap<>();
    for (Repository r : repos) {
      if (r.organization().equals(any.organization()) && r.project().equals(any.project())) {
        byRepo.put(r.key(), r.productionStage());
      }
    }
    return byRepo;
  }

  private int fetchWorkItems(
      String org, String proj, String sinceIso, String token, List<RawEvent> events) {
    String wiql =
        "{\"query\":\"SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '"
            + sinceIso
            + "' ORDER BY [System.ChangedDate] DESC\"}";
    JsonNode ids = client.post(org + "/" + proj + "/_apis/wit/wiql?" + API, token, wiql);
    StringJoiner batch = new StringJoiner(",");
    for (JsonNode wi : ids.path("workItems")) {
      batch.add(wi.path("id").asText());
    }
    if (batch.length() == 0) {
      return 0;
    }
    String fields =
        "System.WorkItemType,System.ChangedDate,System.AssignedTo,Microsoft.VSTS.Scheduling.CompletedWork";
    JsonNode items =
        client.get(
            org + "/_apis/wit/workitems?ids=" + batch + "&fields=" + fields + "&" + API, token);
    int n = 0;
    for (JsonNode wi : arr(items)) {
      events.add(AdoMapper.workItem(wi));
      n++;
    }
    return n;
  }

  private static Predicate<String> aiDetector(AiConvention c) {
    if (c.regex() != null && !c.regex().isBlank()) {
      int flags = c.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
      Pattern p = Pattern.compile(c.regex(), flags);
      return msg -> p.matcher(msg).find();
    }
    String needle = c.trailer() != null ? c.trailer() : (c.tag() != null ? c.tag() : "[ai]");
    String lower = needle.toLowerCase(Locale.ROOT);
    return msg -> msg.toLowerCase(Locale.ROOT).contains(lower);
  }

  /** Accepts a full org URL ({@code https://dev.azure.com/org}) or a short org name. */
  private static String normOrg(String organization) {
    String o = organization.trim().replaceAll("/+$", "");
    return o.startsWith("http") ? o : "https://dev.azure.com/" + o;
  }

  private static boolean before(String isoDate, Instant since) {
    return isoDate.isBlank() || Instant.parse(isoDate).isBefore(since);
  }

  private static Iterable<JsonNode> arr(JsonNode listResponse) {
    return listResponse.path("value");
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
