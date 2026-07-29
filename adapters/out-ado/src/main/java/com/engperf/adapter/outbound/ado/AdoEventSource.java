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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger LOG = LoggerFactory.getLogger(AdoEventSource.class);
  private static final String API = "api-version=7.1";
  // ADO's workitems batch GET accepts at most 200 ids per request.
  private static final int WORKITEM_ID_BATCH = 200;

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
    List<Repository> repos = structure.findRepositories();
    if (repos.isEmpty()) {
      throw new IllegalStateException(
          "nenhum repositório cadastrado — cadastre repositórios em Admin → Repositórios antes de sincronizar");
    }
    String sinceIso = since.toString();
    List<RawEvent> events = new ArrayList<>();
    LOG.info(
        "ADO sync: {} repositório(s) desde {} — {}",
        repos.size(),
        sinceIso,
        repos.stream().map(r -> r.organization() + "/" + r.project() + "/" + r.key()).toList());

    fetchRepoActivity(repos, token, since, sinceIso, progress, events);
    fetchProjectActivity(repos, token, sinceIso, progress, events);

    LOG.info("ADO sync: coleta concluída — {} eventos no total", events.size());
    return events;
  }

  /** Per repository (own org/project/key): completed PRs (+ reviews) and commits since the mark. */
  private void fetchRepoActivity(
      List<Repository> repos,
      String token,
      Instant since,
      String sinceIso,
      ProgressReporter progress,
      List<RawEvent> events) {
    Predicate<String> isAi = aiDetector(config.aiConvention());
    int prs = 0;
    int commits = 0;
    for (Repository repo : repos) {
      String ctx = repo.organization() + "/" + repo.project() + "/" + repo.key();
      LOG.info("ADO sync: repositório {} — PRs + commits", ctx);
      String base =
          normOrg(repo.organization())
              + "/"
              + enc(repo.project())
              + "/_apis/git/repositories/"
              + enc(repo.key());
      try {
        for (JsonNode pr :
            arr(
                client.get(
                    base + "/pullrequests?searchCriteria.status=completed&$top=200&" + API,
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
                    base
                        + "/commits?searchCriteria.fromDate="
                        + enc(sinceIso)
                        + "&$top=1000&"
                        + API,
                    token))) {
          events.add(AdoMapper.commit(c, repo.key(), isAi));
          commits++;
        }
        progress.update("syncing", "commits", commits);
      } catch (RuntimeException e) {
        throw contextual("repositório " + ctx, e);
      }
    }
    LOG.info("ADO sync: {} PR(s) e {} commit(s) coletados", prs, commits);
  }

  /** Per distinct (org, project): pipeline runs (deploys) + work items — project-scoped in ADO. */
  private void fetchProjectActivity(
      List<Repository> repos,
      String token,
      String sinceIso,
      ProgressReporter progress,
      List<RawEvent> events) {
    int deploys = 0;
    int workItems = 0;
    Set<String> seenProjects = new HashSet<>();
    for (Repository repo : repos) {
      if (!seenProjects.add(repo.organization() + "|" + repo.project())) {
        continue;
      }
      String projCtx = repo.organization() + "/" + repo.project();
      LOG.info("ADO sync: projeto {} — pipelines + work items", projCtx);
      String org = normOrg(repo.organization());
      String proj = enc(repo.project());
      Map<String, String> stageBySourceRepo = productionStages(repos, repo);
      try {
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
      } catch (RuntimeException e) {
        throw contextual("projeto " + projCtx, e);
      }
    }
    LOG.info("ADO sync: {} deploy(s) e {} work item(s) coletados", deploys, workItems);
  }

  /** Prefix an error with the repo/project being processed so the failure is self-locating. */
  private static IllegalStateException contextual(String where, RuntimeException cause) {
    LOG.warn("ADO sync: falha no {} — {}", where, cause.getMessage());
    return new IllegalStateException("falha no " + where + ": " + cause.getMessage(), cause);
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
    List<String> ids = collectChangedWorkItemIds(org, proj, sinceIso.substring(0, 10), token);
    if (ids.isEmpty()) {
      return 0;
    }
    String fields =
        "System.WorkItemType,System.ChangedDate,System.AssignedTo,Microsoft.VSTS.Scheduling.CompletedWork";
    int n = 0;
    // ADO caps the workitems batch GET at 200 ids per request — page the id list.
    for (int i = 0; i < ids.size(); i += WORKITEM_ID_BATCH) {
      StringJoiner batch = new StringJoiner(",");
      ids.subList(i, Math.min(i + WORKITEM_ID_BATCH, ids.size())).forEach(batch::add);
      JsonNode items =
          client.get(
              org + "/_apis/wit/workitems?ids=" + batch + "&fields=" + fields + "&" + API, token);
      for (JsonNode wi : arr(items)) {
        events.add(AdoMapper.workItem(wi));
        n++;
      }
    }
    return n;
  }

  /**
   * WIQL caps a result at 20000 rows and cannot be paged (no skip); collect the ids over {@code
   * [sinceDate, today]} by adaptive bisection — a window that exceeds the cap is split in half and
   * retried until each half fits. The column has day precision, so bounds are dates.
   */
  private List<String> collectChangedWorkItemIds(
      String org, String proj, String sinceDate, String token) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    LocalDate start = LocalDate.parse(sinceDate);
    LocalDate end = LocalDate.now(ZoneOffset.UTC).plusDays(1); // exclusive → includes today
    collectRange(org, proj, start, end, token, ids);
    return new ArrayList<>(ids);
  }

  /**
   * Query {@code [from, to)}; if ADO refuses the window for exceeding its cap, bisect and retry.
   */
  private void collectRange(
      String org, String proj, LocalDate from, LocalDate to, String token, Set<String> ids) {
    if (!from.isBefore(to)) {
      return;
    }
    try {
      String wiql =
          "{\"query\":\"SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '"
              + from
              + "' AND [System.ChangedDate] < '"
              + to
              + "' ORDER BY [System.ChangedDate] ASC\"}";
      JsonNode res = client.post(org + "/" + proj + "/_apis/wit/wiql?" + API, token, wiql);
      for (JsonNode wi : res.path("workItems")) {
        ids.add(wi.path("id").asText());
      }
    } catch (RuntimeException e) {
      long days = ChronoUnit.DAYS.between(from, to);
      if (!exceedsWiqlLimit(e) || days <= 1) {
        throw e; // a different failure, or a single day we can no longer subdivide
      }
      LocalDate mid = from.plusDays(days / 2);
      LOG.info(
          "ADO sync: janela {}..{} excede o limite do WIQL; bisseccionando em {}", from, to, mid);
      collectRange(org, proj, from, mid, token, ids);
      collectRange(org, proj, mid, to, token, ids);
    }
  }

  /** The VS402337 "result exceeds 20000 items" refusal — the signal to narrow the window. */
  private static boolean exceedsWiqlLimit(RuntimeException e) {
    String m = e.getMessage();
    return m != null && (m.contains("VS402337") || m.contains("exceeds the size limit"));
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

  // URLEncoder targets application/x-www-form-urlencoded (space -> "+"), but this encodes URL
  // *path segments* — ADO does not decode "+" there, so a space-containing project name (e.g.
  // "Core Card") 404s. Space must be "%20" in a path segment.
  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
