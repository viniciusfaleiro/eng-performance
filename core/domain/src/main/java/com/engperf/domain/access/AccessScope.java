package com.engperf.domain.access;

import java.util.Set;

/**
 * What a signed-in account may see, as predicates over node ids. Aggregate views (vertical/team)
 * are granted org-wide to admin/exec or by structure position; individual (person) views are
 * coaching-only — never granted org-wide, only to admins and to the person's own/managing account.
 */
public record AccessScope(
    boolean admin,
    boolean orgWide,
    Set<String> verticalIds,
    Set<String> teamIds,
    Set<String> personIds) {

  public AccessScope {
    verticalIds = Set.copyOf(verticalIds);
    teamIds = Set.copyOf(teamIds);
    personIds = Set.copyOf(personIds);
  }

  /** Whether the account may reach the configuration (Admin) area. */
  public boolean canConfigure() {
    return admin;
  }

  /** Whether the account may view a structure node (`all`, `v:…`, `t:…`, `p:…`). */
  public boolean canView(String nodeId) {
    if (nodeId == null || nodeId.equals("all")) {
      return admin || orgWide;
    }
    if (nodeId.startsWith("v:")) {
      return admin || orgWide || verticalIds.contains(nodeId);
    }
    if (nodeId.startsWith("t:")) {
      return admin || orgWide || teamIds.contains(nodeId);
    }
    if (nodeId.startsWith("p:")) {
      return canViewIndividual(nodeId);
    }
    return false;
  }

  /** Coaching-only: an individual is visible to admins and to their own/managing account. */
  public boolean canViewIndividual(String personId) {
    return admin || personIds.contains(personId);
  }
}
