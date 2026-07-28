package com.engperf.adapter.outbound.ado;

import com.engperf.application.ado.ProgressReporter;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.outbound.AdoEventSourcePort;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Fetches Azure DevOps activity (api-version 7.1) — projects → repos → PRs/commits, pipeline runs
 * and work items — and maps each to {@link RawEvent} via {@link AdoMapper}. Only activity at or
 * after the watermark is kept. The REST paths here are verified against a real org during
 * acceptance; the mapping (what fills the metric contract) is the fixture-tested part.
 */
@Component
public final class AdoEventSource implements AdoEventSourcePort {

  private static final String API = "api-version=7.1";

  private final AdoRestClient client;
  private final PlatformConfigUseCase config;

  AdoEventSource(AdoRestClient client, PlatformConfigUseCase config) {
    this.client = client;
    this.config = config;
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AdoEventSource(PlatformConfigUseCase config) {
    this(new HttpAdoRestClient(), config);
  }

  @Override
  public List<RawEvent> fetchSince(
      String token,
      String orgUrl,
      String productionStage,
      Instant since,
      ProgressReporter progress) {
    String org = orgUrl.replaceAll("/+$", "");
    String sinceIso = since.toString();
    Predicate<String> isAi = aiDetector(config.aiConvention());
    List<RawEvent> events = new ArrayList<>();
    int prs = 0;
    int commits = 0;
    int deploys = 0;
    int workItems = 0;

    for (JsonNode project : arr(client.get(org + "/_apis/projects?" + API, token))) {
      String proj = enc(project.path("name").asText());

      for (JsonNode repo :
          arr(client.get(org + "/" + proj + "/_apis/git/repositories?" + API, token))) {
        String repoId = repo.path("id").asText();
        String repoName = repo.path("name").asText();

        for (JsonNode pr :
            arr(
                client.get(
                    org
                        + "/"
                        + proj
                        + "/_apis/git/repositories/"
                        + repoId
                        + "/pullrequests?searchCriteria.status=completed&$top=200&"
                        + API,
                    token))) {
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
                    org
                        + "/"
                        + proj
                        + "/_apis/git/repositories/"
                        + repoId
                        + "/commits?searchCriteria.fromDate="
                        + enc(sinceIso)
                        + "&$top=1000&"
                        + API,
                    token))) {
          events.add(AdoMapper.commit(c, repoName, isAi));
          commits++;
        }
        progress.update("syncing", "commits", commits);
      }

      for (JsonNode run :
          arr(
              client.get(
                  org + "/" + proj + "/_apis/build/builds?minTime=" + enc(sinceIso) + "&" + API,
                  token))) {
        AdoMapper.deploy(run, productionStage).ifPresent(events::add);
        deploys++;
      }
      progress.update("syncing", "deploys", deploys);

      workItems += fetchWorkItems(org, proj, sinceIso, token, events);
      progress.update("syncing", "workitems", workItems);
    }
    return events;
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
    String lower = needle.toLowerCase(java.util.Locale.ROOT);
    return msg -> msg.toLowerCase(java.util.Locale.ROOT).contains(lower);
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
