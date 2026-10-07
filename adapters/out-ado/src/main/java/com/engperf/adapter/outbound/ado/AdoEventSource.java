package com.engperf.adapter.outbound.ado;

import com.engperf.application.ado.IngestionResult;
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
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches Azure DevOps activity (api-version 7.1) for the registered repositories across any orgs.
 * Per repo: PRs and commits; per distinct (org, project): pipeline runs (deploys) and work items.
 * Mapping to {@link RawEvent} is by {@link AdoMapper}; REST paths are tuned against a real org.
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
  public IngestionResult fetchSince(String token, Instant since, ProgressReporter progress) {
    List<Repository> repos = structure.findRepositories();
    if (repos.isEmpty()) {
      throw new IllegalStateException(
          "nenhum repositório cadastrado — cadastre repositórios em Admin → Repositórios antes de sincronizar");
    }
    String sinceIso = since.toString();
    List<RawEvent> events = new ArrayList<>();
    FailureLog failures = new FailureLog();
    LOG.info(
        "ADO sync: {} repositório(s) desde {} — {}",
        repos.size(),
        sinceIso,
        repos.stream().map(r -> r.organization() + "/" + r.project() + "/" + r.key()).toList());

    fetchRepoActivity(repos, token, since, sinceIso, progress, events, failures);
    fetchProjectActivity(repos, token, sinceIso, progress, events, failures);

    LOG.info(
        "ADO sync: coleta concluída — {} eventos no total, {} fonte(s) com falha",
        events.size(),
        failures.all().size());
    return new IngestionResult(events, failures.all());
  }

  /** Per repository (own org/project/key): completed PRs (+ reviews) and commits since the mark. */
  private void fetchRepoActivity(
      List<Repository> repos,
      String token,
      Instant since,
      String sinceIso,
      ProgressReporter progress,
      List<RawEvent> events,
      FailureLog failures) {
    Predicate<String> isAi = aiDetector(config.aiConvention());
    CommitComments comments = new CommitComments(client, isAi);
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
          // Duas chamadas por PR (não há endpoint em lote): commits, para tempo e tamanho, e o
          // histórico de votos, porque o objeto do PR guarda só o voto atual de cada revisor.
          long prId = pr.path("pullRequestId").asLong();
          JsonNode prCommits =
              client.get(base + "/pullrequests/" + prId + "/commits?" + API, token);
          VoteHistory votes = VoteHistory.fetch(client, base, prId, token);
          events.add(
              AdoMapper.pullRequest(
                  pr,
                  prCommits,
                  comments.anyAi(base, prCommits, token),
                  votes,
                  // Memoizado por commit: a varredura de IA acima e esta contagem leem a mesma
                  // resposta, então o commit é buscado uma vez e não duas.
                  comments.changedFiles(base, prCommits, token)));
          events.addAll(AdoMapper.reviews(pr, votes));
          prs++;
        }
        progress.update("prs", "prs", prs);
        for (JsonNode c :
            arr(
                client.get(
                    base
                        + "/commits?searchCriteria.fromDate="
                        + enc(sinceIso)
                        + "&$top=1000&"
                        + API,
                    token))) {
          events.add(AdoMapper.commit(comments.full(base, c, token), repo.key(), isAi));
          commits++;
        }
        progress.update("commits", "commits", commits);
      } catch (RuntimeException e) {
        failures.record("repositório " + ctx, e);
      }
    }
    LOG.info(
        "ADO sync: {} PR(s) e {} commit(s) coletados ({} comentário(s) recarregado(s) por truncamento)",
        prs,
        commits,
        comments.reloaded());
  }

  /** Per distinct (org, project): pipeline runs (deploys) + work items — project-scoped in ADO. */
  private void fetchProjectActivity(
      List<Repository> repos,
      String token,
      String sinceIso,
      ProgressReporter progress,
      List<RawEvent> events,
      FailureLog failures) {
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
        deploys += fetchDeploys(org, proj, sinceIso, stageBySourceRepo, token, events);
        progress.update("deploys", "deploys", deploys);
        workItems += fetchWorkItems(org, proj, sinceIso, token, events);
        progress.update("workitems", "workitems", workItems);
      } catch (RuntimeException e) {
        failures.record("projeto " + projCtx, e);
      }
    }
    LOG.info("ADO sync: {} deploy(s) e {} work item(s) coletados", deploys, workItems);
  }

  /**
   * Deploys for one project. A build's stages live only in its Timeline, so for every build whose
   * source repo is registered we fetch its Timeline and emit a DEPLOY for the production stage.
   */
  private int fetchDeploys(
      String org,
      String proj,
      String sinceIso,
      Map<String, String> stageBySourceRepo,
      String token,
      List<RawEvent> events) {
    int deploys = 0;
    for (JsonNode run :
        arr(
            client.get(
                org + "/" + proj + "/_apis/build/builds?minTime=" + enc(sinceIso) + "&" + API,
                token))) {
      String sourceRepo = run.path("repository").path("name").asText("");
      // ADO returns the repo name lower-cased; registered keys keep their case — match
      // case-insensitively, else a real production build is silently dropped as "unregistered".
      String stageRule = stageBySourceRepo.get(sourceRepo.toLowerCase(Locale.ROOT));
      if (stageRule == null) {
        continue; // a run whose source repository is not registered → skipped
      }
      JsonNode timeline =
          client.get(
              org
                  + "/"
                  + proj
                  + "/_apis/build/builds/"
                  + enc(run.path("id").asText())
                  + "/timeline?"
                  + API,
              token);
      for (JsonNode record : timeline.path("records")) {
        if (!"Stage".equalsIgnoreCase(record.path("type").asText(""))) {
          continue;
        }
        Optional<RawEvent> deploy = AdoMapper.deploy(run, record, stageRule);
        if (deploy.isPresent()) {
          events.add(deploy.get());
          deploys++;
        }
      }
    }
    return deploys;
  }

  /** Prefix an error with the repo/project being processed so the failure is self-locating. */
  /**
   * The production-stage rule for each registered repo in the same (org, project) as {@code any}.
   */
  private static Map<String, String> productionStages(List<Repository> repos, Repository any) {
    Map<String, String> byRepo = new HashMap<>();
    for (Repository r : repos) {
      if (r.organization().equals(any.organization()) && r.project().equals(any.project())) {
        byRepo.put(r.key().toLowerCase(Locale.ROOT), r.productionStage());
      }
    }
    return byRepo;
  }

  private int fetchWorkItems(
      String org, String proj, String sinceIso, String token, List<RawEvent> events) {
    List<String> ids =
        ChangedWorkItemIds.collect(client, org, proj, sinceIso.substring(0, 10), token);
    // Toda a lista, sem filtro: quando um work item "some" do dashboard, a primeira pergunta é se a
    // WIQL chegou a devolvê-lo. Com DEBUG ligado dá para grepar qualquer id sem recompilar nada.
    LOG.debug(
        "ADO sync: WIQL {}/{} desde {} devolveu {} work item(s): {}",
        org,
        proj,
        sinceIso,
        ids.size(),
        ids);
    if (ids.isEmpty()) {
      return 0;
    }
    String fields =
        "System.WorkItemType,System.Title,System.ChangedDate,System.CreatedDate,"
            + "System.AssignedTo,System.Parent";
    // Três fases. Coletar tudo antes de mapear é o que permite resolver os pais em lote: uma Task é
    // classificada pelo pai, e perguntar item a item multiplicaria o custo pelo tipo mais numeroso
    // do board.
    List<JsonNode> collected = new ArrayList<>();
    Map<String, String> typeById = new HashMap<>();
    for (int i = 0; i < ids.size(); i += WORKITEM_ID_BATCH) {
      StringJoiner batch = new StringJoiner(",");
      ids.subList(i, Math.min(i + WORKITEM_ID_BATCH, ids.size())).forEach(batch::add);
      JsonNode items =
          client.get(
              org + "/_apis/wit/workitems?ids=" + batch + "&fields=" + fields + "&" + API, token);
      for (JsonNode wi : arr(items)) {
        collected.add(wi);
        // O pai que também mudou na janela já vem aqui — e então não custa chamada nenhuma.
        typeById.put(
            wi.path("id").asText(), wi.path("fields").path("System.WorkItemType").asText(""));
      }
    }
    ParentTypeResolver.resolve(client, org, collected, typeById, token, WORKITEM_ID_BATCH);

    Map<String, Function<String, Segment>> classifierByType = new HashMap<>();
    UnmappedTypes unmapped = new UnmappedTypes();
    Instant now = Instant.now();
    for (JsonNode wi : collected) {
      String type = wi.path("fields").path("System.WorkItemType").asText("");
      if (!AdoMapper.isMappedType(type)) {
        unmapped.record(type);
      }
      Function<String, Segment> classify =
          classifierByType.computeIfAbsent(type, t -> stateClassifier(org, proj, t, token));
      // The update history (one call per item; no batch endpoint) gives the state transitions.
      JsonNode updates =
          client.get(
              org + "/_apis/wit/workitems/" + enc(wi.path("id").asText()) + "/updates?" + API,
              token);
      String parentId = wi.path("fields").path("System.Parent").asText("");
      String parentType = typeById.get(parentId);
      events.add(AdoMapper.workItem(wi, updates, classify, now, org, proj, parentType, parentId));
    }
    unmapped.report(org, proj);
    return collected.size();
  }

  /**
   * Classifies each state of a work-item type into a flow {@link Segment} by its ADO state category
   * (Completed/Resolved/Removed → DONE, Proposed → WAITING, InProgress → REVIEW/ACTIVE by name),
   * cached per type; states without metadata fall back to the name heuristic (never hardcoded).
   */
  private Function<String, Segment> stateClassifier(
      String org, String proj, String type, String token) {
    Map<String, Segment> byName = new HashMap<>();
    try {
      JsonNode states =
          client.get(
              org + "/" + proj + "/_apis/wit/workitemtypes/" + enc(type) + "/states?" + API, token);
      for (JsonNode s : states.path("value")) {
        String name = s.path("name").asText("");
        byName.put(name, StateClassifier.fromCategory(s.path("stateCategory").asText(""), name));
      }
    } catch (RuntimeException e) {
      LOG.warn(
          "ADO sync: sem metadata de estados de '{}' ({}); usando heurística por nome",
          type,
          e.getMessage());
    }
    return state -> byName.getOrDefault(state, StateClassifier.byName(state));
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
