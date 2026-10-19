package com.engperf.domain.metrics;

import java.util.Locale;
import java.util.Set;

/**
 * Whether a work item is a <strong>container</strong> of work or the work itself.
 *
 * <p>A container holds the same work its children do: while an Epic sits open for three months it
 * is counted beside every User Story and Task under it, and the same work is added two and three
 * times. Which reading is the right one depends on the question — "how much is open?" wants the
 * leaves, "how many initiatives are running?" wants the containers — so the rule lives here, in one
 * place, and the readings that need it ask.
 *
 * <p>Being a container is decided by <em>having ingested children</em>, not by type: a User Story
 * that groups Tasks is just as much a container as an Epic, and a team that works directly in a
 * Feature without opening Tasks would otherwise be reported as idle. An Epic is the one exception
 * decided by type — it is a planning artifact whether or not anything was ever linked to it.
 */
public final class WorkItemRole {

  /** The detail key carrying the item's raw Azure DevOps type. */
  private static final String ADO_TYPE = "ado_type";

  private static final String EPIC = "epic";

  private WorkItemRole() {}

  /**
   * Whether {@code item} groups other work.
   *
   * @param parentIds the ids of items that are some other item's parent, over the <strong>whole
   *     ingested corpus</strong> — not the selected period, so an item's classification does not
   *     change when the viewer changes the window
   */
  public static boolean isContainer(RawEvent item, Set<String> parentIds) {
    return parentIds.contains(item.id())
        || EPIC.equals(item.detail().getOrDefault(ADO_TYPE, "").toLowerCase(Locale.ROOT));
  }
}
