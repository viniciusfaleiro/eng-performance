package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The types of the parents a batch of work items refers to but did not include.
 *
 * <p>A task is classified by its parent, and asking for each parent separately would multiply the
 * sync cost by the most numerous work item type there is. This resolves them in pages instead —
 * deduplicated, since several tasks usually hang off the same parent — so the worst case is one
 * request per 200 missing parents rather than one per task.
 *
 * <p>Failure degrades to "nothing resolved": the affected tasks fall back to the default work type,
 * which costs a classification, while propagating would cost the whole ingestion.
 */
final class ParentTypeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ParentTypeResolver.class);
  private static final String API = "api-version=7.1";
  private static final String PARENT = "System.Parent";
  private static final String TYPE = "System.WorkItemType";

  private ParentTypeResolver() {}

  /**
   * Fills {@code typeById} with the types of any parent referenced by {@code items} and not already
   * there. {@code errorPolicy=Omit} keeps one deleted or inaccessible parent from failing the page.
   */
  static void resolve(
      AdoRestClient client,
      String org,
      List<JsonNode> items,
      Map<String, String> typeById,
      String token,
      int pageSize) {
    List<String> missing = List.copyOf(missingParents(items, typeById));
    if (missing.isEmpty()) {
      return;
    }
    for (int i = 0; i < missing.size(); i += pageSize) {
      String page = String.join(",", missing.subList(i, Math.min(i + pageSize, missing.size())));
      try {
        JsonNode res =
            client.get(
                org
                    + "/_apis/wit/workitems?ids="
                    + page
                    + "&fields="
                    + TYPE
                    + "&errorPolicy=Omit&"
                    + API,
                token);
        for (JsonNode wi : res.path("value")) {
          typeById.put(wi.path("id").asText(), wi.path("fields").path(TYPE).asText(""));
        }
      } catch (RuntimeException e) {
        LOG.warn("ADO sync: tipos de work item pai não puderam ser lidos — {}", e.getMessage());
      }
    }
  }

  /** Parent ids referenced by a task-like item and absent from the index, in first-seen order. */
  private static Set<String> missingParents(List<JsonNode> items, Map<String, String> typeById) {
    Set<String> missing = new LinkedHashSet<>();
    for (JsonNode wi : items) {
      String parent = wi.path("fields").path(PARENT).asText("");
      // Self-parent é dado inconsistente; segui-lo daria recursão, então conta como não resolvido.
      if (!parent.isBlank()
          && !parent.equals(wi.path("id").asText())
          && !typeById.containsKey(parent)) {
        missing.add(parent);
      }
    }
    return missing;
  }
}
