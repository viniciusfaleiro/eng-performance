package com.engperf.adapter.outbound.ado;

import com.engperf.application.ado.DiscoveredRepository;
import com.engperf.application.port.outbound.AdoRepositoryDiscoveryPort;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lists a project's Git repositories and, for each, guesses its production-stage rule from recent
 * pipeline runs — the same name heuristic {@link AdoMapper#matchesProduction} already uses as a
 * fallback when classifying deploys, so a discovered repository's suggestion never drifts from what
 * the sync itself would later accept as "production".
 */
@Component
public final class AdoRepositoryDiscovery implements AdoRepositoryDiscoveryPort {

  private static final Logger LOG = LoggerFactory.getLogger(AdoRepositoryDiscovery.class);
  private static final String API = "api-version=7.1";
  // Best-effort sample: recent builds are enough to spot a consistent stage-naming pattern without
  // scanning a project's whole pipeline history.
  private static final int RECENT_BUILDS = 30;
  private static final int BUILDS_PER_REPO_SAMPLE = 5;

  private final AdoRestClient client;

  AdoRepositoryDiscovery(AdoRestClient client) {
    this.client = client;
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AdoRepositoryDiscovery() {
    this(new HttpAdoRestClient());
  }

  @Override
  public List<DiscoveredRepository> discover(
      String accessToken, String organization, String project) {
    String org = normOrg(organization);
    String proj = enc(project);
    List<String> repoNames = new ArrayList<>();
    for (JsonNode r :
        arr(client.get(org + "/" + proj + "/_apis/git/repositories?" + API, accessToken))) {
      repoNames.add(r.path("name").asText(""));
    }

    Map<String, List<JsonNode>> buildsByRepo = recentBuildsByRepo(org, proj, accessToken);

    List<DiscoveredRepository> out = new ArrayList<>();
    for (String name : repoNames) {
      List<JsonNode> builds = buildsByRepo.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
      out.add(new DiscoveredRepository(name, guessProductionStage(org, proj, builds, accessToken)));
    }
    return out;
  }

  private Map<String, List<JsonNode>> recentBuildsByRepo(String org, String proj, String token) {
    Map<String, List<JsonNode>> byRepo = new HashMap<>();
    try {
      for (JsonNode run :
          arr(
              client.get(
                  org + "/" + proj + "/_apis/build/builds?$top=" + RECENT_BUILDS + "&" + API,
                  token))) {
        String repo = run.path("repository").path("name").asText("").toLowerCase(Locale.ROOT);
        if (repo.isBlank()) {
          continue;
        }
        byRepo.computeIfAbsent(repo, k -> new ArrayList<>()).add(run);
      }
    } catch (RuntimeException e) {
      // Best-effort: no pipeline history yet (or no permission to list builds) just means every
      // repository is discovered with no production-stage suggestion, not a failed discovery.
      LOG.warn(
          "ADO discovery: não foi possível listar builds de {}/{} — {}", org, proj, e.getMessage());
    }
    return byRepo;
  }

  /**
   * Distinct stage names matching the production heuristic across a sample of this repo's recent
   * builds; {@code null} unless exactly one distinct candidate is found (see {@code
   * openspec/specs/repo-discovery}).
   */
  private String guessProductionStage(
      String org, String proj, List<JsonNode> builds, String token) {
    Set<String> candidates = new LinkedHashSet<>();
    int sampled = 0;
    for (JsonNode build : builds) {
      if (sampled >= BUILDS_PER_REPO_SAMPLE) {
        break;
      }
      sampled++;
      try {
        JsonNode timeline =
            client.get(
                org
                    + "/"
                    + proj
                    + "/_apis/build/builds/"
                    + enc(build.path("id").asText())
                    + "/timeline?"
                    + API,
                token);
        for (JsonNode record : timeline.path("records")) {
          if (!"Stage".equalsIgnoreCase(record.path("type").asText(""))) {
            continue;
          }
          String stage = record.path("name").asText("");
          if (!stage.isBlank() && AdoMapper.matchesProduction(stage, "")) {
            candidates.add(stage);
          }
        }
      } catch (RuntimeException e) {
        LOG.warn(
            "ADO discovery: falha ao ler timeline do build {} — {}",
            build.path("id").asText(""),
            e.getMessage());
      }
    }
    return candidates.size() == 1 ? candidates.iterator().next() : null;
  }

  private static String normOrg(String organization) {
    String o = organization.trim().replaceAll("/+$", "");
    return o.startsWith("http") ? o : "https://dev.azure.com/" + o;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static Iterable<JsonNode> arr(JsonNode listResponse) {
    return listResponse.path("value");
  }
}
