package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * The work item's parent, as far as one sync could resolve it: which item it is, and its Azure
 * DevOps type when the type index knew it.
 *
 * <p>The two travel together because they answer one question each about the same edge — "what kind
 * of work is this under?" (which classifies a task) and "which item is it under?" (which separates
 * a container from work itself) — and keeping them apart meant every caller passing two loose
 * strings in the right order.
 *
 * <p>A parent whose type could not be read still has its identity recorded. The edge lives on the
 * child on purpose: a parent that was deleted, lies outside the ingested scope, or is not readable
 * does not erase the fact that this item has one.
 */
record ParentRef(String adoType, String adoId) {

  private static final String PARENT = "System.Parent";
  private static final ParentRef NONE = new ParentRef(null, null);

  /** The item with no parent at all. */
  static ParentRef none() {
    return NONE;
  }

  /**
   * The parent {@code wi} points at, with its type taken from {@code typeById}.
   *
   * <p>Self-parent is inconsistent data — following it would recurse — so it counts as no parent,
   * which also keeps the item from showing up as its own child in the parent index.
   */
  static ParentRef of(JsonNode wi, Map<String, String> typeById) {
    String id = wi.path("fields").path(PARENT).asText("");
    if (id.isBlank() || id.equals(wi.path("id").asText())) {
      return NONE;
    }
    return new ParentRef(typeById.get(id), id);
  }
}
