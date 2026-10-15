package com.engperf.adapter.outbound.ado;

import com.engperf.domain.metrics.WorkType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The work-type categories a repository's pull requests inherit from the work items they are linked
 * to.
 *
 * <p>Two phases on purpose. The links themselves have no batch endpoint, so they are asked for one
 * pull request at a time — a small response of ids, next to the two calls per pull request the sync
 * already makes. The <em>types</em> behind those ids are then resolved in pages of the same batch
 * endpoint the work-item sync uses, deduplicated across pull requests: several pull requests
 * usually hang off the same handful of items, and asking per pull request would multiply the cost
 * of the sync by the busiest board.
 *
 * <p>A failure resolves to "no link known", which leaves the pull request without a work type. That
 * is the honest outcome: with a work-type filter active such a pull request is excluded and
 * counted, rather than being given a plausible category nobody could doubt on screen.
 */
final class PullRequestWorkTypes {

  private static final Logger LOG = LoggerFactory.getLogger(PullRequestWorkTypes.class);
  private static final String API = "api-version=7.1";
  private static final String TYPE = "System.WorkItemType";
  private static final String PARENT = "System.Parent";

  private final Map<Long, Set<WorkType>> byPullRequest;

  private PullRequestWorkTypes(Map<Long, Set<WorkType>> byPullRequest) {
    this.byPullRequest = byPullRequest;
  }

  /** The categories of one pull request — empty when it is linked to no work item we could read. */
  Set<WorkType> of(long pullRequestId) {
    return byPullRequest.getOrDefault(pullRequestId, Set.of());
  }

  /**
   * Resolves the categories of every given pull request of one repository.
   *
   * @param base the repository's REST base path, as the sync builds it
   * @param org the organization path segment, for the (org-scoped) work-item batch endpoint
   */
  static PullRequestWorkTypes resolve(
      AdoRestClient client,
      String org,
      String base,
      List<Long> pullRequestIds,
      String token,
      int pageSize) {
    Map<Long, List<String>> itemsByPr = new LinkedHashMap<>();
    Set<String> allIds = new LinkedHashSet<>();
    for (long prId : pullRequestIds) {
      List<String> ids = linkedItems(client, base, prId, token);
      if (!ids.isEmpty()) {
        itemsByPr.put(prId, ids);
        allIds.addAll(ids);
      }
    }
    Map<String, WorkType> categoryById = categories(client, org, allIds, token, pageSize);

    Map<Long, Set<WorkType>> out = new LinkedHashMap<>();
    for (Map.Entry<Long, List<String>> e : itemsByPr.entrySet()) {
      EnumSet<WorkType> types = EnumSet.noneOf(WorkType.class);
      for (String id : e.getValue()) {
        WorkType t = categoryById.get(id);
        if (t != null) {
          types.add(t);
        }
      }
      if (!types.isEmpty()) {
        out.put(e.getKey(), types);
      }
    }
    return new PullRequestWorkTypes(out);
  }

  /** The ids of the work items one pull request is linked to; empty when none or unreadable. */
  private static List<String> linkedItems(
      AdoRestClient client, String base, long prId, String token) {
    try {
      JsonNode res = client.get(base + "/pullrequests/" + prId + "/workitems?" + API, token);
      List<String> ids = new ArrayList<>();
      for (JsonNode ref : res.path("value")) {
        String id = ref.path("id").asText("");
        if (!id.isBlank()) {
          ids.add(id);
        }
      }
      return ids;
    } catch (RuntimeException e) {
      LOG.warn("ADO sync: vínculos do PR {} não puderam ser lidos — {}", prId, e.getMessage());
      return List.of();
    }
  }

  /**
   * The category of each work item id, in pages. A task is classified by its parent, exactly as in
   * the work-item sync, so a pull request linked to "write the test" inherits the nature of the
   * story or the debt item it belongs to, instead of the nature of a task.
   */
  private static Map<String, WorkType> categories(
      AdoRestClient client, String org, Set<String> ids, String token, int pageSize) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<String> all = List.copyOf(ids);
    List<JsonNode> collected = new ArrayList<>();
    Map<String, String> typeById = new HashMap<>();
    for (int i = 0; i < all.size(); i += pageSize) {
      StringJoiner page = new StringJoiner(",");
      all.subList(i, Math.min(i + pageSize, all.size())).forEach(page::add);
      try {
        JsonNode res =
            client.get(
                org
                    + "/_apis/wit/workitems?ids="
                    + page
                    + "&fields="
                    + TYPE
                    + ","
                    + PARENT
                    + "&errorPolicy=Omit&"
                    + API,
                token);
        for (JsonNode wi : res.path("value")) {
          collected.add(wi);
          typeById.put(wi.path("id").asText(), wi.path("fields").path(TYPE).asText(""));
        }
      } catch (RuntimeException e) {
        LOG.warn(
            "ADO sync: tipos dos work items vinculados a PRs não puderam ser lidos — {}",
            e.getMessage());
      }
    }
    ParentTypeResolver.resolve(client, org, collected, typeById, token, pageSize);

    Map<String, WorkType> out = new HashMap<>();
    for (JsonNode wi : collected) {
      String id = wi.path("id").asText();
      String type = wi.path("fields").path(TYPE).asText("");
      String parentType = typeById.get(wi.path("fields").path(PARENT).asText(""));
      out.put(id, AdoWorkTypes.category(type, parentType));
    }
    return out;
  }
}
